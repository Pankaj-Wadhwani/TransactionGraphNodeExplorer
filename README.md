# Transaction Graph Node Explorer

Spring Boot REST service that loads a static hierarchical transaction graph and, given a **node id**, returns that node's details, its **level**, **parent chain**, **direct children**, and **transactions** (including next-level child transactions).

## Versions

- Java 17 (runs on JDK 17+; developed against JDK 21)
- Spring Boot 3.4.5
- Maven 3.9+

## How to run

```bash
mvn test
mvn spring-boot:run
```

The API listens on `http://localhost:8080`.

## Dataset

On startup the app loads `src/main/resources/transactions-graph-nodes.json` into memory:

- `Map<String, GraphNode> nodeById`
- `Map<String, List<GraphNode>> childrenByParentId`

The provided file has 20 nodes and 100 transactions. Nodes are **not** assumed to be ordered. Notable cases:

| Node | Notes |
|------|--------|
| `N1`, `N14`, `N18` | True roots (`parentId` is null), level 0 |
| `N19` | `parentId` is `N99`, which is missing — treated as root-like (level 0, empty parent chain, `isRoot=true`) |
| `N8` | Deep node: `N1 → N2 → N4 → N8` (level 3) |

## Endpoints

### `GET /api/graph/nodes/{id}`

`{id}` is a **node** id (`N1`, `N2`, …), not a transaction id.

Query parameters:

| Name | Default | Rules |
|------|---------|--------|
| `maxDepth` | `1` | Integer `0`–`10`. Values outside that range return HTTP 400 `INVALID_MAX_DEPTH`. |

Response includes:

1. Node details: `id`, `parentId`, `name`, `accountNumber`
2. `level` — `0` for roots / missing parent; otherwise `parent.level + 1`
3. `parentChain` — ancestors from the **root down to this node's direct parent**
4. `children` — **direct** children only (sorted by id)
5. `transactions` — this node's transactions
6. `nextLevelTransactions` — transactions on direct children, each tagged with `nodeId` / `nodeName`
7. `isRoot`, `isLeaf`
8. `childrenTree` — nested expansion up to `maxDepth` (bonus)
9. `levelAggregates` — subtree totals grouped by absolute graph level (bonus)

Example:

```bash
curl http://localhost:8080/api/graph/nodes/N4
curl "http://localhost:8080/api/graph/nodes/N1?maxDepth=3"
```

404 example (`N999`):

```json
{ "error": "NODE_NOT_FOUND", "message": "Graph node N999 does not exist" }
```

### `GET /api/graph/nodes/{id}/children-transactions`

Optional filters over **direct child node** transactions:

| Name | Meaning |
|------|---------|
| `minAmount` | Inclusive lower bound |
| `maxAmount` | Inclusive upper bound |
| `txnType` | `POS`, `ATM`, `TRANSFER`, or `SALARY` (case-insensitive) |

```bash
curl "http://localhost:8080/api/graph/nodes/N1/children-transactions?txnType=POS&minAmount=1400&maxAmount=1500"
```

If `minAmount > maxAmount`, the API returns HTTP 400 `INVALID_FILTER`.

## Error codes

| HTTP | `error` | When |
|------|---------|------|
| 404 | `NODE_NOT_FOUND` | Unknown node id |
| 400 | `INVALID_MAX_DEPTH` | `maxDepth` &lt; 0 or &gt; 10 |
| 400 | `INVALID_FILTER` | Bad amount range |
| 400 | `CYCLE_DETECTED` | `parentId` walk or child expansion hits a repeated node |

## Assumptions

- **Level** of a node with a missing parent is 0, even if `parentId` is non-null (`N19` in the sample file).
- **`totalAmount`** in `levelAggregates` is the sum of transaction `amount` values at that level, **not** netted by direction. Amounts in the dataset are already non-negative; direction is a separate field.
- **`netAmount`** is `creditTotal - debitTotal` at that level.
- **`creditTotal` / `debitTotal`** split `totalAmount` by `direction`.
- Direct children and child transactions are ordered by child node id, then the order of transactions in the JSON.
- Cycles are not present in the sample file; detection uses a visited set so traversal cannot loop.

More design notes: [DESIGN.md](DESIGN.md). Extra curl samples: [scripts/curl-examples.sh](scripts/curl-examples.sh).
