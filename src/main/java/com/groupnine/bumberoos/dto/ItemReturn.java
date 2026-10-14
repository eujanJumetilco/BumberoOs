package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.enums.ItemCondition;
import jakarta.validation.constraints.NotNull;

/** Condition an item came back in when an incident is resolved. */
public record ItemReturn(
        @NotNull Long itemId,
        @NotNull ItemCondition condition
) {}
