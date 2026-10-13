package com.groupnine.bumberoos.domain.entities;

import com.groupnine.bumberoos.domain.enums.IncidentSeverityLevel;
import com.groupnine.bumberoos.domain.enums.IncidentStatus;
import com.groupnine.bumberoos.util.StringUtil;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor
public class IncidentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentSeverityLevel severityLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    private String incidentType;
    private String location;
    private LocalDateTime dateTimeReported;
    private String reportedBy;

    public IncidentEntity(
            IncidentSeverityLevel severityLevel, IncidentStatus status,
            String incidentType, String location,
            LocalDateTime dateTimeReported, String reportedBy
    ) {
        this.severityLevel = severityLevel;
        this.status = status;
        this.incidentType = incidentType;
        this.location = location;
        this.dateTimeReported = dateTimeReported;
        this.reportedBy = reportedBy;
        normalize();
    }

    @PrePersist
    @PreUpdate
    public void normalize() {
        incidentType = StringUtil.toTitleCase(incidentType);
        location = StringUtil.capitalizeFirstLetter(location);
        reportedBy = StringUtil.toTitleCase(reportedBy);
    }
}
