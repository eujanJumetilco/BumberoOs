package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.enums.ItemCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Rules for one item category. {@code lowStockThreshold} 0 means no low-stock alert.
 * {@code maintenanceIntervalDays} null means use the default; 0 means no reminder for this category.
 */
public record CategoryRuleDto(
        @NotNull ItemCategory category,
        @Min(0) int lowStockThreshold,
        @Min(0) Integer maintenanceIntervalDays
) {}
