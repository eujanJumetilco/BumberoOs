package com.groupnine.bumberoos.domain.exceptions;

/** The request is valid but conflicts with current state (maps to 409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
