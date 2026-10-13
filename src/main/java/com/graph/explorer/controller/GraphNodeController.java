package com.graph.explorer.controller;

import com.graph.explorer.dto.ApiDtos.ChildrenTransactionsResponse;
import com.graph.explorer.dto.ApiDtos.NodeExplorerResponse;
import com.graph.explorer.service.GraphExplorerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/graph/nodes")
public class GraphNodeController {

    private final GraphExplorerService graphExplorerService;

    public GraphNodeController(GraphExplorerService graphExplorerService) {
        this.graphExplorerService = graphExplorerService;
    }

    @GetMapping("/{id}")
    public NodeExplorerResponse getNode(
            @PathVariable String id,
            @RequestParam(required = false) Integer maxDepth
    ) {
        return graphExplorerService.getNode(id, maxDepth);
    }

    @GetMapping("/{id}/children-transactions")
    public ChildrenTransactionsResponse getChildrenTransactions(
            @PathVariable String id,
            @RequestParam(required = false) Double minAmount,
            @RequestParam(required = false) Double maxAmount,
            @RequestParam(required = false) String txnType
    ) {
        return graphExplorerService.getChildrenTransactions(id, minAmount, maxAmount, txnType);
    }
}
