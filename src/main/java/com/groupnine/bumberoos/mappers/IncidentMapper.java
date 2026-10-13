package com.groupnine.bumberoos.mappers;

import com.groupnine.bumberoos.domain.entities.IncidentEntity;
import com.groupnine.bumberoos.dto.IncidentRequest;
import com.groupnine.bumberoos.dto.IncidentResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class IncidentMapper {

    /** A missing report time defaults to now. */
    public IncidentEntity toEntity(IncidentRequest request) {
        return new IncidentEntity(
                request.severityLevel(), request.status(),
                request.incidentType(), request.location(),
                request.dateTimeReported() != null ? request.dateTimeReported() : LocalDateTime.now(),
                request.reportedBy()
        );
    }

    public IncidentResponse toResponse(IncidentEntity incident) {
        return new IncidentResponse(
                incident.getId(), incident.getSeverityLevel(), incident.getStatus(),
                incident.getIncidentType(), incident.getLocation(),
                incident.getDateTimeReported(), incident.getReportedBy()
        );
    }

    public List<IncidentResponse> toResponses(List<IncidentEntity> incidents) {
        return incidents.stream().map(this::toResponse).toList();
    }
}
