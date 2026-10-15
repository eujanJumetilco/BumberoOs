package com.groupnine.bumberoos.dto;

import java.time.LocalDateTime;

public record CommanderOrderResponse(
        String instruction,
        LocalDateTime issuedAt
) {}
