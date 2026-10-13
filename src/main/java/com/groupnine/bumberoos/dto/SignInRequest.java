package com.groupnine.bumberoos.dto;

import jakarta.validation.constraints.NotBlank;

public record SignInRequest(
        @NotBlank String email,
        @NotBlank String password
) {
    /** Keeps the password out of logs and exception messages. */
    @Override
    public String toString() {
        return "SignInRequest[email=" + email + ", password=***]";
    }
}
