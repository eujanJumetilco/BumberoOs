package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.enums.AlarmLevel;
import com.groupnine.bumberoos.domain.enums.IncidentNature;
import com.groupnine.bumberoos.domain.enums.IncidentSeverityLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Payload for reporting an incident, optionally assigning staff and items in the same call.
 * There is no status field: new incidents are always ONGOING.
 */
public record IncidentRequest(
        @NotNull IncidentNature nature,
        AlarmLevel alarmLevel,
        @NotBlank String incidentType,
        @NotBlank String location,
        @NotBlank String reportedBy,
        LocalDateTime dateTimeReported,
        @Deprecated IncidentSeverityLevel severityLevel,
        List<@NotNull Long> staffIds,
        List<@Valid @NotNull ItemLine> items
) {}
