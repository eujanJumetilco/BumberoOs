package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.domain.entities.IncidentEntity;
import com.groupnine.bumberoos.domain.enums.AlarmLevel;
import com.groupnine.bumberoos.domain.enums.IncidentNature;
import com.groupnine.bumberoos.domain.enums.IncidentStatus;
import com.groupnine.bumberoos.util.CsvSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV / PSV export and import for incidents.
 *
 * Kept separate from the main incident controller (it shares the /api/incidents path). It talks to the
 * database through the EntityManager so it depends only on IncidentEntity. If you would rather route the
 * import through your incident service, replace {@code entityManager::persist} below with the service's
 * create method.
 *
 * Only the flat incident record is covered. Staff and equipment assignments, alarm history and commander
 * orders are separate tables and are not exported or imported.
 */
@RestController
@RequestMapping("/api/incidents")
public class IncidentCsvController {

    /** Export column order. ID is informational: import creates NEW incidents and ignores it. */
    private static final List<String> CSV_HEADERS = List.of(
            "ID", "Nature", "Status", "Alarm Level", "Incident Type", "Location", "Latitude", "Longitude",
            "Date Reported", "Reported By", "Arrived At", "Under Control At", "Fire Out At", "Resolved At",
            "Commander Name");

    private final EntityManager entityManager;

    public IncidentCsvController(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** GET /api/incidents/export?format=csv|psv[&status=ONGOING|RESOLVED]. Newest first. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportFile(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) IncidentStatus status
    ) {
        String jpql = "select i from IncidentEntity i"
                + (status != null ? " where i.status = :status" : "")
                + " order by i.dateTimeReported desc, i.id desc";
        TypedQuery<IncidentEntity> query = entityManager.createQuery(jpql, IncidentEntity.class);
        if (status != null) query.setParameter("status", status);

        List<List<Object>> rows = new ArrayList<>();
        for (IncidentEntity i : query.getResultList()) {
            rows.add(CsvSupport.row(
                    i.getId(), CsvSupport.name(i.getNature()), CsvSupport.name(i.getStatus()),
                    CsvSupport.name(i.getAlarmLevel()), CsvSupport.safe(i.getIncidentType()),
                    CsvSupport.safe(i.getLocation()), i.getLatitude(), i.getLongitude(),
                    i.getDateTimeReported(), CsvSupport.safe(i.getReportedBy()),
                    i.getArrivedAt(), i.getUnderControlAt(), i.getFireOutAt(), i.getResolvedAt(),
                    CsvSupport.safe(i.getCommanderName())));
        }
        return CsvSupport.export("incidents", CsvSupport.Format.of(format), CSV_HEADERS, rows);
    }

    /**
     * POST /api/incidents/import (multipart field "file", CSV or PSV). Every row becomes a NEW incident.
     * Required: Nature, Incident Type, Location, Date Reported, Reported By. Status defaults to ONGOING.
     * All-or-nothing: one bad row rejects the whole file.
     */
    @PostMapping("/import")
    @Transactional(rollbackFor = Exception.class)
    public CsvSupport.ImportResult importFile(@RequestParam("file") MultipartFile file) {
        CsvSupport.Table table = CsvSupport.parse(file);
        table.require(CSV_HEADERS, "nature", "incident type|type", "location",
                "date reported|reported at", "reported by");
        return CsvSupport.importRows(table, this::toEntity, entityManager::persist);
    }

    private IncidentEntity toEntity(CsvSupport.Row row) {
        IncidentNature nature = CsvSupport.parseEnum(IncidentNature.class, row.get("nature"), "Nature");
        String incidentType = row.get("incident type", "type");
        String location = row.get("location");
        LocalDateTime reported = CsvSupport.parseDateTime(row.get("date reported", "reported at"), "Date Reported");
        String reportedBy = row.get("reported by");
        if (nature == null || incidentType == null || location == null || reported == null || reportedBy == null) {
            throw new IllegalArgumentException(
                    "Nature, incident type, location, date reported and reported by are required.");
        }

        IncidentStatus status = CsvSupport.parseEnum(IncidentStatus.class, row.get("status"), "Status");
        if (status == null) status = IncidentStatus.ONGOING;

        AlarmLevel alarmLevel = CsvSupport.parseEnum(AlarmLevel.class, row.get("alarm level", "alarm"), "Alarm Level");
        if (alarmLevel != null && nature != IncidentNature.EMERGENCY) {
            throw new IllegalArgumentException("Alarm level only applies to EMERGENCY incidents.");
        }

        Double latitude = CsvSupport.parseDouble(row.get("latitude", "lat"), "Latitude");
        Double longitude = CsvSupport.parseDouble(row.get("longitude", "lng", "lon"), "Longitude");
        CsvSupport.requireCoordinates(latitude, longitude);

        LocalDateTime resolvedAt = CsvSupport.parseDateTime(row.get("resolved at"), "Resolved At");
        if (resolvedAt != null && status != IncidentStatus.RESOLVED) {
            throw new IllegalArgumentException("Resolved At only applies to RESOLVED incidents.");
        }

        IncidentEntity incident = new IncidentEntity(nature, incidentType, location, reported, reportedBy);
        incident.setStatus(status);
        incident.setAlarmLevel(alarmLevel);
        incident.setLatitude(latitude);
        incident.setLongitude(longitude);
        incident.setArrivedAt(CsvSupport.parseDateTime(row.get("arrived at"), "Arrived At"));
        incident.setUnderControlAt(CsvSupport.parseDateTime(row.get("under control at"), "Under Control At"));
        incident.setFireOutAt(CsvSupport.parseDateTime(row.get("fire out at"), "Fire Out At"));
        incident.setResolvedAt(resolvedAt);
        incident.setCommanderName(row.get("commander name", "commander"));
        return incident;
    }
}
