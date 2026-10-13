package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.enums.IncidentSeverityLevel;
import com.groupnine.bumberoos.domain.enums.IncidentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Payload for reporting an incident. */
public record IncidentRequest(
        @NotNull IncidentSeverityLevel severityLevel,
        @NotNull IncidentStatus status,
        @NotBlank String incidentType,
        @NotBlank String location,
        LocalDateTime dateTimeReported,
        @NotBlank String reportedBy
) {}
