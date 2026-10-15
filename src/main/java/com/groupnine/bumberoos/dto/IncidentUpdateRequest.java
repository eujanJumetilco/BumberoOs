package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.enums.AlarmLevel;
import com.groupnine.bumberoos.domain.enums.IncidentNature;
import com.groupnine.bumberoos.domain.enums.IncidentSeverityLevel;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.time.LocalDateTime;

/** Partial update: any null field is left unchanged. Status changes go through resolve/reopen. */
public record IncidentUpdateRequest(
        IncidentNature nature,
        AlarmLevel alarmLevel,
        String incidentType,
        String location,
        String reportedBy,
        LocalDateTime dateTimeReported,
        LocalDateTime underControlAt,
        LocalDateTime fireOutAt,
        @Deprecated IncidentSeverityLevel severityLevel,
        LocalDateTime arrivedAt,
        String commanderName,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude
) {}
