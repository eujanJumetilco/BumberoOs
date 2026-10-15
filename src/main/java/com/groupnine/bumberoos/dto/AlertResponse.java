package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.enums.AlertSeverity;
import com.groupnine.bumberoos.domain.enums.AlertType;
import com.groupnine.bumberoos.domain.enums.ItemCategory;

/** One thing that needs attention. {@code itemId} is null for category-wide alerts such as LOW_STOCK. */
public record AlertResponse(
        AlertType type,
        AlertSeverity severity,
        Long itemId,
        String itemName,
        ItemCategory category,
        String message
) {}
