package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.enums.ItemCondition;

import java.time.LocalDate;

public record ItemResponse(
        Long id,
        String name,
        String description,
        int quantity,
        ItemCondition condition,
        LocalDate lastMaintained
) {}
