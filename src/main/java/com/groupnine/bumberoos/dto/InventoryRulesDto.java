package com.groupnine.bumberoos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Inventory rules, used for both the response and the replace-all update.
 * {@code defaultMaintenanceIntervalDays} 0 turns maintenance reminders off unless a category sets its own.
 */
public record InventoryRulesDto(
        @NotNull @Min(0) Integer defaultMaintenanceIntervalDays,
        @NotNull @Min(0) Integer expiryWarningDays,
        List<@Valid @NotNull CategoryRuleDto> categories
) {}
