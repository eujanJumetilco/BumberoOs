package com.groupnine.bumberoos.domain.exceptions;

/** The request is invalid regardless of current state (maps to 400). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
