package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.enums.IncidentSeverityLevel;
import com.groupnine.bumberoos.domain.enums.IncidentStatus;

import java.time.LocalDateTime;

/** Partial update: any null field is left unchanged. */
public record IncidentUpdateRequest(
        IncidentSeverityLevel severityLevel,
        IncidentStatus status,
        String incidentType,
        String location,
        LocalDateTime dateTimeReported,
        String reportedBy
) {}
