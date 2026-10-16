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


## Scaling

**Today:** data is loaded once and lookups are O(1), but derived values (level, parent chain, cycle checks, per-level aggregates) are recomputed on every request. Scale in this order, adding each step only when measurements justify it.

1. **Precompute at load time.** One BFS from the roots assigns every node's level and detects cycles once (unreached nodes are in a cycle, so the load fails). Store the parent chain and per-level subtree totals so requests become lookups. Use iteration instead of recursion for very deep trees.
2. **Atomic reloads.** Keep all indexes and precomputed values in one immutable `Snapshot` behind an `AtomicReference`. A reload builds the new snapshot privately and swaps it in with one write, so readers never see a mix and a bad file leaves the old data live.
3. **Cache and add instances.** Cache responses by `(nodeId, maxDepth)`, cleared on snapshot swap. Run several identical stateless instances behind a load balancer; each holds its own snapshot, so reads scale almost linearly.
4. **Database when data outgrows memory.** Put `GraphStore` behind an interface. Keep the small tree structure in memory and move transactions to a table indexed on `(node_id, amount, txn_type)`. Use a closure table `(ancestor, descendant, depth)` so per-level aggregates become one `GROUP BY depth` query.
