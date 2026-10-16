package com.graph.explorer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graph.explorer.dto.ApiDtos.ChildrenTransactionsResponse;
import com.graph.explorer.dto.ApiDtos.LevelAggregate;
import com.graph.explorer.dto.ApiDtos.NodeExplorerResponse;
import com.graph.explorer.exception.GraphCycleException;
import com.graph.explorer.exception.InvalidFilterException;
import com.graph.explorer.exception.InvalidMaxDepthException;
import com.graph.explorer.exception.NodeNotFoundException;
import com.graph.explorer.model.GraphNode;
import com.graph.explorer.model.NodeTransaction;
import com.graph.explorer.service.GraphExplorerService;
import com.graph.explorer.store.GraphStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphExplorerServiceTest {

    private GraphExplorerService service;

    @BeforeEach
    void setUp() throws Exception {
        GraphStore store = new GraphStore(new ObjectMapper(), new ClassPathResource("transactions-graph-nodes.json"));
        store.load();
        service = new GraphExplorerService(store);
    }

    @Test
    void rootNodeHasLevelZeroAndDirectChildren() {
        NodeExplorerResponse n1 = service.getNode("N1", 1);

        assertEquals(0, n1.level());
        assertTrue(n1.isRoot());
        assertFalse(n1.isLeaf());
        assertTrue(n1.parentChain().isEmpty());
        assertEquals(List.of("N2", "N3"), n1.children().stream().map(c -> c.id()).toList());
        assertEquals(5, n1.transactions().size());
        assertEquals(10, n1.nextLevelTransactions().size());
        assertEquals("N2", n1.nextLevelTransactions().get(0).nodeId());
    }

    @Test
    void parentChainIsRootDownToDirectParent() {
        NodeExplorerResponse n8 = service.getNode("N8", 1);

        assertEquals(3, n8.level());
        assertFalse(n8.isRoot());
        assertTrue(n8.isLeaf());
        assertEquals(List.of("N1", "N2", "N4"), n8.parentChain().stream().map(p -> p.id()).toList());
        assertEquals(List.of(0, 1, 2), n8.parentChain().stream().map(p -> p.level()).toList());
    }

    @Test
    void missingParentIsTreatedAsRoot() {
        NodeExplorerResponse n19 = service.getNode("N19", 1);

        assertEquals(0, n19.level());
        assertTrue(n19.isRoot());
        assertTrue(n19.isLeaf());
        assertTrue(n19.parentChain().isEmpty());
        assertEquals("N99", n19.parentId());
    }

    @Test
    void unknownNodeReturnsNotFound() {
        NodeNotFoundException ex = assertThrows(NodeNotFoundException.class, () -> service.getNode("N999", 1));
        assertEquals("N999", ex.getNodeId());
    }

    @Test
    void invalidMaxDepthIsRejected() {
        assertThrows(InvalidMaxDepthException.class, () -> service.getNode("N1", -1));
        assertThrows(InvalidMaxDepthException.class, () -> service.getNode("N1", 11));
    }

    @Test
    void childrenTreeHonorsMaxDepth() {
        NodeExplorerResponse depth1 = service.getNode("N1", 1);
        assertEquals(2, depth1.childrenTree().children().size());
        assertTrue(depth1.childrenTree().children().get(0).children().isEmpty());

        NodeExplorerResponse depth2 = service.getNode("N1", 2);
        assertEquals(2, depth2.childrenTree().children().get(0).children().size());
        assertEquals("N4", depth2.childrenTree().children().get(0).children().get(0).id());
    }

    @Test
    void levelAggregatesCoverTheSubtree() {
        NodeExplorerResponse n1 = service.getNode("N1", 1);
        List<Integer> levels = n1.levelAggregates().stream().map(LevelAggregate::level).toList();
        assertEquals(List.of(0, 1, 2, 3), levels);

        LevelAggregate level0 = n1.levelAggregates().get(0);
        assertEquals(1, level0.nodeCount());
        assertEquals(5, level0.transactionCount());
    }

    @Test
    void childrenTransactionsCanBeFiltered() {
        ChildrenTransactionsResponse filtered = service.getChildrenTransactions("N1", 1400.0, 1500.0, "POS");
        assertEquals(1, filtered.childrenTransactions().size());
        assertEquals("T10", filtered.childrenTransactions().get(0).txnId());
    }

    @Test
    void minAmountGreaterThanMaxAmountIsRejected() {
        assertThrows(InvalidFilterException.class,
                () -> service.getChildrenTransactions("N1", 2000.0, 100.0, null));
    }

    @Test
    void parentCycleIsDetectedWithoutLooping() {
        GraphStore cyclic = new GraphStore(new ObjectMapper(), new ClassPathResource("transactions-graph-nodes.json"));
        GraphNode a = node("A", "B");
        GraphNode b = node("B", "A");
        cyclic.index(List.of(a, b));
        GraphExplorerService cyclicService = new GraphExplorerService(cyclic);

        GraphCycleException ex = assertThrows(GraphCycleException.class, () -> cyclicService.getNode("A", 1));
        assertTrue(ex.getMessage().contains("Cycle"));
    }

    private static GraphNode node(String id, String parentId) {
        GraphNode node = new GraphNode();
        node.setId(id);
        node.setParentId(parentId);
        node.setName(id);
        node.setAccountNumber("ACC-" + id);
        NodeTransaction txn = new NodeTransaction();
        txn.setTxnId("T-" + id);
        txn.setDirection("CREDIT");
        txn.setTxnType("TRANSFER");
        txn.setAmount(1);
        node.setTransactions(List.of(txn));
        return node;
    }
}
