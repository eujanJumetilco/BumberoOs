package com.groupnine.bumberoos.dto;

import java.time.LocalDateTime;

public record StaffAssignmentResponse(
        Long staffId,
        String name,
        String role,
        LocalDateTime assignedAt,
        LocalDateTime releasedAt
) {}
