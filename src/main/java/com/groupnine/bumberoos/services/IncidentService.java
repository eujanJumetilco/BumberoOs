package com.groupnine.bumberoos.services;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;

import com.groupnine.bumberoos.domain.entities.IncidentEntity;
import com.groupnine.bumberoos.util.TextStorageUtil;


@Service
public class IncidentService {

    private static final String FILE_NAME = "data/incident_data.txt";

    private final ArrayList<IncidentEntity> incidents = new ArrayList<>();
    private int nextId = 1;

    public IncidentService() {
        ArrayList<IncidentEntity> loaded = TextStorageUtil.loadIncidentsFromTextFile(FILE_NAME);
        incidents.addAll(loaded);

        for (IncidentEntity i : incidents) {
            if (i.getId() >= nextId) {
                nextId = i.getId() + 1;
            }
        }
    }

    public void saveAll() {
        TextStorageUtil.saveIncidentsToTextFile(incidents, FILE_NAME);
    }

    public int getNextId() {
        return nextId;
    }


    // =====> ADD <===== //

    public IncidentEntity addIncident(IncidentEntity incident) {
        incident.setId(nextId++);
        incidents.add(incident);
        saveAll();
        return incident;
    }


    // =====> REMOVE <===== //

    /**
     * Removes an incident by ID.
     * @return true if found and removed, false otherwise.
     */
    public boolean removeIncident(int id) {
        boolean removed = incidents.removeIf(i -> i.getId() == id);

        if (removed) {
            saveAll();
        }

        return removed;
    }


    // =====> EDIT <===== //

    /**
     * Overwrites editable fields on an existing incident record.
     * Pass null to leave a String/LocalDateTime field unchanged,
     * and -1 to leave an int-coded field unchanged.
     *
     * @param id               ID of the incident to update
     * @param severityCode     0=LOW,1=MEDIUM,2=HIGH,3=CRITICAL; -1 to keep current
     * @param statusCode       0=ONGOING,1=PENDING; -1 to keep current
     * @param incidentType     new type, or null to keep current
     * @param location         new location, or null to keep current
     * @param dateTimeReported new date-time, or null to keep current
     * @param reportedBy       new reporter, or null to keep current
     * @return the updated IncidentEntity, or null if not found
     */
    public IncidentEntity editIncident(
            int id,
            int severityCode,
            int statusCode,
            String incidentType,
            String location,
            LocalDateTime dateTimeReported,
            String reportedBy
    ) {
        IncidentEntity incident = findById(id);
        if (incident == null) return null;

        if (severityCode   >= 0) incident.setSeverityLevel(severityCode);
        if (statusCode     >= 0) incident.setStatus(statusCode);
        if (incidentType   != null) incident.setIncidentType(incidentType);
        if (location       != null) incident.setLocation(location);
        if (dateTimeReported != null) incident.setDateTimeReported(dateTimeReported);
        if (reportedBy     != null) incident.setReportedBy(reportedBy);

        saveAll();
        return incident;
    }


    // =====> QUERIES <===== //

    public IncidentEntity findById(int id) {
        return incidents.stream()
                .filter(i -> i.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public ArrayList<IncidentEntity> getAllIncidents() {
        return new ArrayList<>(incidents);
    }
}