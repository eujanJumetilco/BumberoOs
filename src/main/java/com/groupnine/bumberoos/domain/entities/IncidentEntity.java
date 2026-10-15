package com.groupnine.bumberoos.domain.entities;

import com.groupnine.bumberoos.domain.enums.AlarmLevel;
import com.groupnine.bumberoos.domain.enums.IncidentNature;
import com.groupnine.bumberoos.domain.enums.IncidentSeverityLevel;
import com.groupnine.bumberoos.domain.enums.IncidentStatus;
import com.groupnine.bumberoos.util.StringUtil;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private IncidentNature nature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status = IncidentStatus.ONGOING;

    /** Only meaningful when {@link #nature} is EMERGENCY. */
    @Enumerated(EnumType.STRING)
    private AlarmLevel alarmLevel;

    /** @deprecated superseded by {@link #alarmLevel}. */
    @Deprecated
    @Enumerated(EnumType.STRING)
    private IncidentSeverityLevel severityLevel;

    private String incidentType;
    private String location;
    private LocalDateTime dateTimeReported;
    private String reportedBy;

    private LocalDateTime underControlAt;
    private LocalDateTime fireOutAt;
    private LocalDateTime resolvedAt;

    /** When the brigade arrived at the scene. */
    private LocalDateTime arrivedAt;

    /** The BFP Incident Commander the brigade reported to. */
    private String commanderName;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IncidentStaffAssignment> staffAssignments = new ArrayList<>();

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IncidentItemAssignment> itemAssignments = new ArrayList<>();

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("raisedAt ASC, id ASC")
    private List<IncidentAlarmEvent> alarmEvents = new ArrayList<>();

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("issuedAt ASC, id ASC")
    private List<IncidentCommanderOrder> commanderOrders = new ArrayList<>();

    public IncidentEntity(
            IncidentNature nature, String incidentType, String location,
            LocalDateTime dateTimeReported, String reportedBy
    ) {
        this.nature = nature;
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
        commanderName = StringUtil.toTitleCase(commanderName);
        if (commanderName != null && commanderName.isEmpty()) commanderName = null;
    }
}
