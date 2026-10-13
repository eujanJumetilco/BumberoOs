package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank String email,
        @NotBlank @Size(min = 8) String password,
        String displayName,
        UserRole role
) {
    /** Keeps the password out of logs and exception messages. */
    @Override
    public String toString() {
        return "SignUpRequest[email=" + email + ", password=***, displayName=" + displayName + ", role=" + role + "]";
    }
}
