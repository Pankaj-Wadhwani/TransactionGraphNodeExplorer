package com.graph.explorer.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graph.explorer.model.GraphDataset;
import com.graph.explorer.model.GraphNode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GraphStore {

    private final ObjectMapper objectMapper;
    private final Resource dataset;

    private Map<String, GraphNode> nodeById = Map.of();
    private Map<String, List<GraphNode>> childrenByParentId = Map.of();

    public GraphStore(ObjectMapper objectMapper, @Value("${graph.dataset}") Resource dataset) {
        this.objectMapper = objectMapper;
        this.dataset = dataset;
    }

    @PostConstruct
    public void load() throws IOException {
        try (InputStream in = dataset.getInputStream()) {
            GraphDataset graphDataset = objectMapper.readValue(in, GraphDataset.class);
            index(graphDataset.getNodes());
        }
    }

    public void index(List<GraphNode> nodes) {
        Map<String, GraphNode> byId = new HashMap<>();
        Map<String, List<GraphNode>> children = new HashMap<>();

        for (GraphNode node : nodes) {
            if (node.getId() == null || node.getId().isBlank()) {
                continue;
            }
            byId.put(node.getId(), node);
        }

        for (GraphNode node : byId.values()) {
            String parentId = node.getParentId();
            if (parentId != null && !parentId.isBlank()) {
                children.computeIfAbsent(parentId, key -> new ArrayList<>()).add(node);
            }
        }

        for (List<GraphNode> siblings : children.values()) {
            siblings.sort(Comparator.comparing(GraphNode::getId, Comparator.nullsLast(String::compareTo)));
        }

        this.nodeById = Collections.unmodifiableMap(byId);
        this.childrenByParentId = Collections.unmodifiableMap(children);
    }

    public GraphNode getNode(String id) {
        return nodeById.get(id);
    }

    public List<GraphNode> getChildren(String parentId) {
        return childrenByParentId.getOrDefault(parentId, List.of());
    }

    public Map<String, GraphNode> getNodeById() {
        return nodeById;
    }
}
