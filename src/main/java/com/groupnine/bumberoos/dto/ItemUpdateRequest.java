package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.enums.ItemCategory;
import com.groupnine.bumberoos.domain.enums.ItemCondition;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

/** Partial update: any null field is left unchanged. */
public record ItemUpdateRequest(
        String name,
        String description,
        @Min(0) Integer quantity,
        ItemCondition condition,
        LocalDate lastMaintained,
        ItemCategory category,
        String serialNumber,
        LocalDate expiryDate
) {}
