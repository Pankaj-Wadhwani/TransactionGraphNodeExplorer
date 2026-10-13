package com.graph.explorer.exception;

import com.graph.explorer.dto.ApiDtos.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NodeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NodeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NODE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler({InvalidMaxDepthException.class, InvalidFilterException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException ex) {
        String code = ex instanceof InvalidMaxDepthException ? "INVALID_MAX_DEPTH" : "INVALID_FILTER";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(code, ex.getMessage()));
    }

    @ExceptionHandler(GraphCycleException.class)
    public ResponseEntity<ErrorResponse> handleCycle(GraphCycleException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("CYCLE_DETECTED", ex.getMessage()));
    }
}
