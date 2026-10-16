package com.graph.explorer.dto;

import java.util.List;

public final class ApiDtos {

    private ApiDtos() {
    }

    public record ErrorResponse(String error, String message) {
    }

    public record ParentChainEntry(String id, String name, String accountNumber, int level) {
    }

    public record ChildNodeSummary(
            String id,
            String parentId,
            String name,
            String accountNumber,
            int level,
            boolean isLeaf,
            int transactionCount
    ) {
    }

    public record TransactionView(
            String txnId,
            String direction,
            String txnType,
            double amount,
            String currency,
            String timestamp,
            String description
    ) {
    }

    public record ChildTransactionView(
            String nodeId,
            String nodeName,
            String txnId,
            String direction,
            String txnType,
            double amount,
            String currency,
            String timestamp,
            String description
    ) {
    }

    public record ChildrenTreeNode(
            String id,
            String parentId,
            String name,
            String accountNumber,
            int level,
            List<TransactionView> transactions,
            List<ChildrenTreeNode> children
    ) {
    }

    public record LevelAggregate(
            int level,
            int nodeCount,
            int transactionCount,
            double totalAmount,
            double creditTotal,
            double debitTotal,
            double netAmount
    ) {
    }

    public record NodeExplorerResponse(
            String id,
            String parentId,
            String name,
            String accountNumber,
            int level,
            boolean isRoot,
            boolean isLeaf,
            List<ParentChainEntry> parentChain,
            List<ChildNodeSummary> children,
            List<TransactionView> transactions,
            List<ChildTransactionView> nextLevelTransactions,
            ChildrenTreeNode childrenTree,
            List<LevelAggregate> levelAggregates
    ) {
    }

    public record ChildrenTransactionsResponse(
            String id,
            String name,
            int level,
            List<ChildTransactionView> childrenTransactions
    ) {
    }
}
