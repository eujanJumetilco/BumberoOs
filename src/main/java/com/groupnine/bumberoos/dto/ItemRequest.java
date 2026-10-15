package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.enums.ItemCategory;
import com.groupnine.bumberoos.domain.enums.ItemCondition;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Payload for adding an inventory item. {@code category} defaults to OTHER. */
public record ItemRequest(
        @NotBlank String name,
        String description,
        @Min(0) int quantity,
        @NotNull ItemCondition condition,
        ItemCategory category,
        String serialNumber,
        LocalDate expiryDate
) {}
