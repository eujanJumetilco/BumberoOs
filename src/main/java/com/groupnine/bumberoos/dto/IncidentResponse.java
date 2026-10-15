package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.enums.AlarmLevel;
import com.groupnine.bumberoos.domain.enums.IncidentNature;
import com.groupnine.bumberoos.domain.enums.IncidentSeverityLevel;
import com.groupnine.bumberoos.domain.enums.IncidentStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code staff} and {@code items} include released assignments (history); {@code deployedStaffIds}
 * lists active staff only and is kept for backward compatibility.
 */
public record IncidentResponse(
        Long id,
        IncidentNature nature,
        IncidentStatus status,
        AlarmLevel alarmLevel,
        @Deprecated IncidentSeverityLevel severityLevel,
        String incidentType,
        String location,
        String reportedBy,
        LocalDateTime dateTimeReported,
        LocalDateTime underControlAt,
        LocalDateTime fireOutAt,
        LocalDateTime resolvedAt,
        List<Long> deployedStaffIds,
        List<StaffAssignmentResponse> staff,
        List<ItemAssignmentResponse> items,
        List<AlarmEventResponse> alarmHistory,
        LocalDateTime arrivedAt,
        String commanderName,
        List<CommanderOrderResponse> commanderOrders
) {}
