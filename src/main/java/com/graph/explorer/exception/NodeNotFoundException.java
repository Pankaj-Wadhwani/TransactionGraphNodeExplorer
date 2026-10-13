package com.graph.explorer.exception;

public class NodeNotFoundException extends RuntimeException {

    private final String nodeId;

    public NodeNotFoundException(String nodeId) {
        super("Graph node " + nodeId + " does not exist");
        this.nodeId = nodeId;
    }

    public String getNodeId() {
        return nodeId;
    }
}
