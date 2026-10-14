package com.groupnine.bumberoos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Units of one item to send to an incident. */
public record ItemLine(
        @NotNull Long itemId,
        @Min(1) int quantity
) {}
