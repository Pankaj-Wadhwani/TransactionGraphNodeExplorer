# Design

## Loading

`GraphStore` reads `transactions-graph-nodes.json` from the classpath at startup (`@PostConstruct`) with Jackson.

Two indexes are built:

- `nodeById` — O(1) lookup for `GET /api/graph/nodes/{id}`
- `childrenByParentId` — adjacency list of **direct** children, sorted by id so responses are stable even though the JSON is unordered

An in-memory map is enough for ~20 nodes / 100 transactions. Swapping the store for H2 (or any DB) would not change the service or controller.

## Level, parent chain, children

For a requested node:

1. If `parentId` is null/blank, or the parent id is not in `nodeById`, the node is **root-like**: `level = 0`, `parentChain = []`, `isRoot = true`.
2. Otherwise walk `parentId` toward the top, recording ancestors. The walk uses a **visited set** (including the start node) so a cycle cannot infinite-loop.
3. Reverse the ancestor list so `parentChain` is **root → … → direct parent**. Each entry carries that ancestor's own level (`0` at the root of this chain).
4. The node's level is `directParent.level + 1`.
5. Children come only from `childrenByParentId.get(id)` — never grandchildren. `isLeaf` is whether that list is empty.

`nextLevelTransactions` is the concatenation of each direct child's transactions, tagged with the child node id.

## Depth-limited tree

`maxDepth` (default 1, allowed 0–10) controls `childrenTree`. Depth 1 is the node plus direct children with empty nested `children`. Recursion passes a **path-scoped** visited set: a node may appear in sibling branches, but repeating an ancestor is a cycle and aborts with `CYCLE_DETECTED`.

## Aggregates

`levelAggregates` walks the **full subtree** rooted at the requested node (not limited by `maxDepth`) and groups by **absolute** graph level:

- `nodeCount`, `transactionCount`
- `totalAmount` — sum of `amount` (unsigned; direction is separate)
- `creditTotal` / `debitTotal` / `netAmount`

The same path-scoped visited set is used.

## Orphans and cycles

| Case | Behaviour |
|------|-----------|
| Missing parent (`N19` → `N99`) | Treat as level-0 root-like. Do not fail the request. |
| Cycle on the parent walk | HTTP 400 `CYCLE_DETECTED` |
| Cycle while expanding children / subtree | HTTP 400 `CYCLE_DETECTED` |

With a single `parentId` per node, a cycle in the child adjacency list is the same cycle as in the parent pointers; both walks are guarded anyway so a future multi-edge dataset would still terminate.

## Scaling / extensions

- **Larger graphs**: keep the same indexes; add a depth cap on aggregate walks; consider precomputing `level` and `parentChain` at load time (Kahn / DFS with cycle check once).
- **Mutability**: wrap `GraphStore` behind an interface and persist nodes in a table `(id, parent_id, …)` plus `transactions`. Recursive CTEs can replace in-memory DFS.
- **Auth / multi-tenant**: a `GraphId` on every node, scoped indexes per graph.
- **Search**: secondary indexes on `accountNumber`, `txnType`, and time range; the children-transactions endpoint is the start of that path.
