package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.enums.IncidentSeverityLevel;
import com.groupnine.bumberoos.domain.enums.IncidentStatus;

import java.time.LocalDateTime;

public record IncidentResponse(
        Long id,
        IncidentSeverityLevel severityLevel,
        IncidentStatus status,
        String incidentType,
        String location,
        LocalDateTime dateTimeReported,
        String reportedBy
) {}
