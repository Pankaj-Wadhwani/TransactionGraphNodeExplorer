#!/usr/bin/env bash
# Sample calls against a locally running server (mvn spring-boot:run).
BASE="${BASE:-http://localhost:8080}"

echo "== N1 root =="
curl -s "$BASE/api/graph/nodes/N1" | head -c 400
echo

echo "== N4 with parent chain =="
curl -s "$BASE/api/graph/nodes/N4"

echo
echo "== N19 orphan parent =="
curl -s "$BASE/api/graph/nodes/N19"

echo
echo "== missing node =="
curl -s -i "$BASE/api/graph/nodes/N999" | head -n 20

echo
echo "== maxDepth=3 =="
curl -s "$BASE/api/graph/nodes/N1?maxDepth=3" | head -c 400
echo

echo "== filtered child txns =="
curl -s "$BASE/api/graph/nodes/N1/children-transactions?txnType=POS&minAmount=1400&maxAmount=1500"
echo
