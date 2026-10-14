package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.enums.AlarmLevel;

import java.time.LocalDateTime;

public record AlarmEventResponse(
        AlarmLevel alarmLevel,
        LocalDateTime raisedAt
) {}
