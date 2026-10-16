package com.graph.explorer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GraphNodeControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getNodeReturnsHierarchyPayload() throws Exception {
        mockMvc.perform(get("/api/graph/nodes/N4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("N4"))
                .andExpect(jsonPath("$.level").value(2))
                .andExpect(jsonPath("$.isRoot").value(false))
                .andExpect(jsonPath("$.isLeaf").value(false))
                .andExpect(jsonPath("$.parentChain", hasSize(2)))
                .andExpect(jsonPath("$.parentChain[0].id").value("N1"))
                .andExpect(jsonPath("$.parentChain[1].id").value("N2"))
                .andExpect(jsonPath("$.children", hasSize(2)))
                .andExpect(jsonPath("$.children[0].id").value("N8"))
                .andExpect(jsonPath("$.nextLevelTransactions", hasSize(10)))
                .andExpect(jsonPath("$.levelAggregates").isArray());
    }

    @Test
    void missingNodeReturns404Payload() throws Exception {
        mockMvc.perform(get("/api/graph/nodes/N999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NODE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Graph node N999 does not exist"));
    }

    @Test
    void invalidMaxDepthReturns400() throws Exception {
        mockMvc.perform(get("/api/graph/nodes/N1").param("maxDepth", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_MAX_DEPTH"));
    }

    @Test
    void childrenTransactionsFilterEndpointWorks() throws Exception {
        mockMvc.perform(get("/api/graph/nodes/N2/children-transactions")
                        .param("txnType", "ATM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("N2"))
                .andExpect(jsonPath("$.childrenTransactions").isArray());
    }
}
