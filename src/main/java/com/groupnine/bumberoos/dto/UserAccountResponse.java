package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.enums.UserRole;

import java.time.LocalDateTime;

/** Public view of an account. Deliberately has no password or hash field. */
public record UserAccountResponse(
        Long id,
        String email,
        String displayName,
        UserRole role,
        boolean enabled,
        Long staffId,
        LocalDateTime createdAt
) {}
