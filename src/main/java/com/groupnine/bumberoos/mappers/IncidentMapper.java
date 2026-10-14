package com.groupnine.bumberoos.mappers;

import com.groupnine.bumberoos.domain.entities.IncidentEntity;
import com.groupnine.bumberoos.domain.entities.IncidentItemAssignment;
import com.groupnine.bumberoos.domain.entities.IncidentStaffAssignment;
import com.groupnine.bumberoos.dto.*;
import com.groupnine.bumberoos.services.DispatchService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class IncidentMapper {

    /** A missing report time defaults to now. Staff/item assignments are handled by DispatchService. */
    @SuppressWarnings("deprecation")
    public IncidentEntity toEntity(IncidentRequest request) {
        IncidentEntity incident = new IncidentEntity(
                request.nature(), request.incidentType(), request.location(),
                request.dateTimeReported() != null ? request.dateTimeReported() : LocalDateTime.now(),
                request.reportedBy()
        );
        incident.setAlarmLevel(request.alarmLevel());
        incident.setSeverityLevel(request.severityLevel());
        return incident;
    }

    public List<DispatchService.ItemRequestLine> toItemLines(List<ItemLine> lines) {
        if (lines == null) return List.of();
        return lines.stream()
                .map(l -> new DispatchService.ItemRequestLine(l.itemId(), l.quantity()))
                .toList();
    }

    public List<DispatchService.ItemReturnLine> toItemReturns(ResolveRequest request) {
        if (request == null || request.itemReturns() == null) return List.of();
        return request.itemReturns().stream()
                .map(r -> new DispatchService.ItemReturnLine(r.itemId(), r.condition()))
                .toList();
    }

    @SuppressWarnings("deprecation")
    public IncidentResponse toResponse(IncidentEntity incident) {
        List<StaffAssignmentResponse> staff = incident.getStaffAssignments().stream()
                .map(a -> new StaffAssignmentResponse(
                        a.getStaff().getId(), a.getStaff().getName(), a.getRole(),
                        a.getAssignedAt(), a.getReleasedAt()))
                .toList();
        List<ItemAssignmentResponse> items = incident.getItemAssignments().stream()
                .map(a -> new ItemAssignmentResponse(
                        a.getItem().getId(), a.getItem().getName(), a.getItem().getCategory(),
                        a.getQuantity(), a.getAssignedAt(), a.getReleasedAt(), a.getReturnCondition()))
                .toList();
        List<AlarmEventResponse> alarmHistory = incident.getAlarmEvents().stream()
                .map(e -> new AlarmEventResponse(e.getAlarmLevel(), e.getRaisedAt()))
                .toList();
        List<Long> deployedStaffIds = incident.getStaffAssignments().stream()
                .filter(IncidentStaffAssignment::isActive)
                .map(a -> a.getStaff().getId())
                .toList();

        return new IncidentResponse(
                incident.getId(), incident.getNature(), incident.getStatus(),
                incident.getAlarmLevel(), incident.getSeverityLevel(),
                incident.getIncidentType(), incident.getLocation(), incident.getReportedBy(),
                incident.getDateTimeReported(), incident.getUnderControlAt(),
                incident.getFireOutAt(), incident.getResolvedAt(),
                deployedStaffIds, staff, items, alarmHistory
        );
    }

    public List<IncidentResponse> toResponses(List<IncidentEntity> incidents) {
        return incidents.stream().map(this::toResponse).toList();
    }
}
