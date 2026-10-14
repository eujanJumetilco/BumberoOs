package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.enums.ItemCategory;
import com.groupnine.bumberoos.domain.enums.ItemCondition;

import java.time.LocalDateTime;

public record ItemAssignmentResponse(
        Long itemId,
        String name,
        ItemCategory category,
        int quantity,
        LocalDateTime assignedAt,
        LocalDateTime releasedAt,
        ItemCondition returnCondition
) {}
