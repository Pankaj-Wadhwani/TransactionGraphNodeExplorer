package com.graph.explorer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphDataset {

    private List<GraphNode> nodes = new ArrayList<>();

    public List<GraphNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<GraphNode> nodes) {
        this.nodes = nodes == null ? new ArrayList<>() : nodes;
    }
}
