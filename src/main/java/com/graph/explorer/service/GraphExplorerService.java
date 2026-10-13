package com.graph.explorer.service;

import com.graph.explorer.dto.ApiDtos.ChildNodeSummary;
import com.graph.explorer.dto.ApiDtos.ChildTransactionView;
import com.graph.explorer.dto.ApiDtos.ChildrenTransactionsResponse;
import com.graph.explorer.dto.ApiDtos.ChildrenTreeNode;
import com.graph.explorer.dto.ApiDtos.LevelAggregate;
import com.graph.explorer.dto.ApiDtos.NodeExplorerResponse;
import com.graph.explorer.dto.ApiDtos.ParentChainEntry;
import com.graph.explorer.dto.ApiDtos.TransactionView;
import com.graph.explorer.exception.GraphCycleException;
import com.graph.explorer.exception.InvalidFilterException;
import com.graph.explorer.exception.InvalidMaxDepthException;
import com.graph.explorer.exception.NodeNotFoundException;
import com.graph.explorer.model.GraphNode;
import com.graph.explorer.model.NodeTransaction;
import com.graph.explorer.store.GraphStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class GraphExplorerService {

    static final int DEFAULT_MAX_DEPTH = 1;
    static final int MAX_ALLOWED_DEPTH = 10;

    private final GraphStore graphStore;

    public GraphExplorerService(GraphStore graphStore) {
        this.graphStore = graphStore;
    }

    public NodeExplorerResponse getNode(String id, Integer maxDepth) {
        int depth = maxDepth == null ? DEFAULT_MAX_DEPTH : maxDepth;
        validateMaxDepth(depth);

        GraphNode node = requireNode(id);
        Ancestry ancestry = resolveAncestry(node);
        List<GraphNode> children = graphStore.getChildren(node.getId());

        List<ChildTransactionView> nextLevelTransactions = new ArrayList<>();
        for (GraphNode child : children) {
            nextLevelTransactions.addAll(toChildTransactions(child));
        }

        return new NodeExplorerResponse(
                node.getId(),
                node.getParentId(),
                node.getName(),
                node.getAccountNumber(),
                ancestry.level(),
                ancestry.root(),
                children.isEmpty(),
                ancestry.parentChain(),
                children.stream().map(child -> toChildSummary(child, ancestry.level() + 1)).toList(),
                toTransactions(node),
                nextLevelTransactions,
                buildChildrenTree(node, ancestry.level(), depth, new HashSet<>(Set.of(node.getId()))),
                computeLevelAggregates(node, ancestry.level())
        );
    }

    public ChildrenTransactionsResponse getChildrenTransactions(
            String id,
            Double minAmount,
            Double maxAmount,
            String txnType
    ) {
        if (minAmount != null && maxAmount != null && minAmount > maxAmount) {
            throw new InvalidFilterException("minAmount cannot be greater than maxAmount");
        }

        GraphNode node = requireNode(id);
        Ancestry ancestry = resolveAncestry(node);
        List<ChildTransactionView> matches = new ArrayList<>();

        for (GraphNode child : graphStore.getChildren(node.getId())) {
            for (ChildTransactionView txn : toChildTransactions(child)) {
                if (minAmount != null && txn.amount() < minAmount) {
                    continue;
                }
                if (maxAmount != null && txn.amount() > maxAmount) {
                    continue;
                }
                if (txnType != null && !txnType.isBlank()
                        && (txn.txnType() == null || !txn.txnType().equalsIgnoreCase(txnType))) {
                    continue;
                }
                matches.add(txn);
            }
        }

        return new ChildrenTransactionsResponse(node.getId(), node.getName(), ancestry.level(), matches);
    }

    private GraphNode requireNode(String id) {
        GraphNode node = graphStore.getNode(id);
        if (node == null) {
            throw new NodeNotFoundException(id);
        }
        return node;
    }

    private void validateMaxDepth(int maxDepth) {
        if (maxDepth < 0 || maxDepth > MAX_ALLOWED_DEPTH) {
            throw new InvalidMaxDepthException(
                    "maxDepth must be between 0 and " + MAX_ALLOWED_DEPTH + " (inclusive)");
        }
    }

    /**
     * Walks parentId toward the root. A missing parent makes the node root-like (level 0, empty chain).
     * A repeated id on the walk is a cycle.
     */
    Ancestry resolveAncestry(GraphNode node) {
        if (node.getParentId() == null || node.getParentId().isBlank()) {
            return new Ancestry(0, true, List.of());
        }

        GraphNode parent = graphStore.getNode(node.getParentId());
        if (parent == null) {
            return new Ancestry(0, true, List.of());
        }

        List<GraphNode> ancestorsNewestFirst = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        visited.add(node.getId());

        GraphNode current = parent;
        while (current != null) {
            if (!visited.add(current.getId())) {
                throw new GraphCycleException(
                        "Cycle detected in parent chain of node " + node.getId() + " at " + current.getId());
            }
            ancestorsNewestFirst.add(current);

            if (current.getParentId() == null || current.getParentId().isBlank()) {
                break;
            }
            GraphNode next = graphStore.getNode(current.getParentId());
            if (next == null) {
                break;
            }
            current = next;
        }

        List<ParentChainEntry> chain = new ArrayList<>();
        for (int i = ancestorsNewestFirst.size() - 1; i >= 0; i--) {
            GraphNode ancestor = ancestorsNewestFirst.get(i);
            int level = ancestorsNewestFirst.size() - 1 - i;
            chain.add(new ParentChainEntry(
                    ancestor.getId(),
                    ancestor.getName(),
                    ancestor.getAccountNumber(),
                    level
            ));
        }

        int nodeLevel = chain.isEmpty() ? 0 : chain.get(chain.size() - 1).level() + 1;
        return new Ancestry(nodeLevel, false, chain);
    }

    private ChildNodeSummary toChildSummary(GraphNode child, int childLevel) {
        boolean leaf = graphStore.getChildren(child.getId()).isEmpty();
        int txnCount = child.getTransactions() == null ? 0 : child.getTransactions().size();
        return new ChildNodeSummary(
                child.getId(),
                child.getParentId(),
                child.getName(),
                child.getAccountNumber(),
                childLevel,
                leaf,
                txnCount
        );
    }

    private List<TransactionView> toTransactions(GraphNode node) {
        if (node.getTransactions() == null) {
            return List.of();
        }
        return node.getTransactions().stream().map(this::toTransactionView).toList();
    }

    private TransactionView toTransactionView(NodeTransaction txn) {
        return new TransactionView(
                txn.getTxnId(),
                txn.getDirection(),
                txn.getTxnType(),
                txn.getAmount(),
                txn.getCurrency(),
                txn.getTimestamp(),
                txn.getDescription()
        );
    }

    private List<ChildTransactionView> toChildTransactions(GraphNode child) {
        if (child.getTransactions() == null) {
            return List.of();
        }
        return child.getTransactions().stream()
                .map(txn -> new ChildTransactionView(
                        child.getId(),
                        child.getName(),
                        txn.getTxnId(),
                        txn.getDirection(),
                        txn.getTxnType(),
                        txn.getAmount(),
                        txn.getCurrency(),
                        txn.getTimestamp(),
                        txn.getDescription()
                ))
                .toList();
    }

    private ChildrenTreeNode buildChildrenTree(GraphNode node, int level, int remainingDepth, Set<String> path) {
        List<ChildrenTreeNode> nested = new ArrayList<>();
        if (remainingDepth > 0) {
            for (GraphNode child : graphStore.getChildren(node.getId())) {
                if (!path.add(child.getId())) {
                    throw new GraphCycleException(
                            "Cycle detected while expanding children of " + node.getId() + " at " + child.getId());
                }
                nested.add(buildChildrenTree(child, level + 1, remainingDepth - 1, path));
                path.remove(child.getId());
            }
        }
        return new ChildrenTreeNode(
                node.getId(),
                node.getParentId(),
                node.getName(),
                node.getAccountNumber(),
                level,
                toTransactions(node),
                nested
        );
    }

    /**
     * Aggregates the subtree rooted at {@code root} by absolute graph level.
     * totalAmount is the sum of transaction amounts (all positive in this dataset),
     * regardless of CREDIT/DEBIT. netAmount is CREDIT minus DEBIT.
     */
    private List<LevelAggregate> computeLevelAggregates(GraphNode root, int rootLevel) {
        Map<Integer, MutableAggregate> byLevel = new LinkedHashMap<>();
        collectSubtree(root, rootLevel, new HashSet<>(Set.of(root.getId())), byLevel);

        List<LevelAggregate> result = new ArrayList<>();
        List<Integer> levels = new ArrayList<>(byLevel.keySet());
        levels.sort(Comparator.naturalOrder());
        for (Integer level : levels) {
            MutableAggregate agg = byLevel.get(level);
            result.add(new LevelAggregate(
                    level,
                    agg.nodeCount,
                    agg.transactionCount,
                    round(agg.totalAmount),
                    round(agg.creditTotal),
                    round(agg.debitTotal),
                    round(agg.creditTotal - agg.debitTotal)
            ));
        }
        return result;
    }

    private void collectSubtree(
            GraphNode node,
            int level,
            Set<String> path,
            Map<Integer, MutableAggregate> byLevel
    ) {
        MutableAggregate agg = byLevel.computeIfAbsent(level, key -> new MutableAggregate());
        agg.nodeCount++;
        if (node.getTransactions() != null) {
            for (NodeTransaction txn : node.getTransactions()) {
                agg.transactionCount++;
                agg.totalAmount += txn.getAmount();
                String direction = txn.getDirection() == null ? "" : txn.getDirection().toUpperCase(Locale.ROOT);
                if ("CREDIT".equals(direction)) {
                    agg.creditTotal += txn.getAmount();
                } else if ("DEBIT".equals(direction)) {
                    agg.debitTotal += txn.getAmount();
                }
            }
        }

        for (GraphNode child : graphStore.getChildren(node.getId())) {
            if (!path.add(child.getId())) {
                throw new GraphCycleException(
                        "Cycle detected in subtree of " + node.getId() + " at " + child.getId());
            }
            collectSubtree(child, level + 1, path, byLevel);
            path.remove(child.getId());
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    record Ancestry(int level, boolean root, List<ParentChainEntry> parentChain) {
    }

    private static final class MutableAggregate {
        private int nodeCount;
        private int transactionCount;
        private double totalAmount;
        private double creditTotal;
        private double debitTotal;
    }
}
