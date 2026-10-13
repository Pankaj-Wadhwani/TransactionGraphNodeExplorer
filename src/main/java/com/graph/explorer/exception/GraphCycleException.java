package com.graph.explorer.exception;

public class GraphCycleException extends RuntimeException {

    public GraphCycleException(String message) {
        super(message);
    }
}
