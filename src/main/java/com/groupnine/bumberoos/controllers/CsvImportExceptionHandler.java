package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.util.CsvImportException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Returns the import's row errors in the response body. Spring Boot hides exception messages from error
 * responses by default, which would leave the UI showing only "Request failed (400)".
 * The shape (status / error / message) matches Spring's default error JSON, plus a full "errors" list.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CsvImportExceptionHandler {

    @ExceptionHandler(CsvImportException.class)
    public ResponseEntity<Map<String, Object>> handle(CsvImportException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "error", "Bad Request",
                "message", ex.getMessage(),
                "errors", ex.getErrors()));
    }
}
