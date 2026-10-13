package com.groupnine.bumberoos.domain.entities;

import com.groupnine.bumberoos.domain.enums.IncidentStatus;
import com.groupnine.bumberoos.domain.enums.IncidentSeverityLevel;
import com.groupnine.bumberoos.util.StringUtil;

import java.io.Serializable;
import java.time.LocalDateTime;

public class IncidentEntity implements Serializable {

    private int id;
    private IncidentSeverityLevel incidentSeverityLevel;
    private IncidentStatus status;

    private String incidentType;
    private String location;
    private LocalDateTime dateTimeReported;
    private String reportedBy;

    public IncidentEntity(
            int id, int severityLevel, int status, String incidentType,
            String location, LocalDateTime dateTimeReported, String reportedBy
    ) {
        setId(id);
        setSeverityLevel(severityLevel);
        setStatus(status);
        setIncidentType(incidentType);
        setLocation(location);
        setDateTimeReported(dateTimeReported);
        setReportedBy(reportedBy);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public IncidentSeverityLevel getSeverityLevel() {
        return incidentSeverityLevel;
    }

    public String getSeverityLevelAsString() {
        return switch (this.incidentSeverityLevel) {
            case LOW -> "Low";
            case MEDIUM -> "Medium";
            case HIGH -> "High";
            case CRITICAL -> "Critical";
            default -> null;
        };
    }

    public void setSeverityLevel(int input) {
        this.incidentSeverityLevel = IncidentSeverityLevel.setSeverity(input);
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public String getStatusAsString() {
        return switch (this.status) {
            case ONGOING -> "Ongoing";
            case PENDING -> "Pending";
            default -> null;
        };
    }

    public void setStatus(int input) {
        this.status = IncidentStatus.setStatus(input);
    }

    public String getIncidentType() {
        return incidentType;
    }

    public void setIncidentType(String incidentType) {
        this.incidentType = StringUtil.toTitleCase(incidentType);
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = StringUtil.capitalizeFirstLetter(location);
    }

    public LocalDateTime getDateTimeReported() {
        return dateTimeReported;
    }

    public void setDateTimeReported(LocalDateTime dateTimeReported) {
        this.dateTimeReported = dateTimeReported;
    }

    public String getReportedBy() {
        return reportedBy;
    }

    public void setReportedBy(String reportedBy) {
        this.reportedBy = StringUtil.toTitleCase(reportedBy);
    }
}