package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.IncidentAlarmEvent;
import com.groupnine.bumberoos.domain.entities.IncidentCommanderOrder;
import com.groupnine.bumberoos.domain.entities.IncidentEntity;
import com.groupnine.bumberoos.domain.enums.AlarmLevel;
import com.groupnine.bumberoos.domain.enums.IncidentNature;
import com.groupnine.bumberoos.domain.enums.IncidentSeverityLevel;
import com.groupnine.bumberoos.domain.enums.IncidentStatus;
import com.groupnine.bumberoos.domain.exceptions.BadRequestException;
import com.groupnine.bumberoos.domain.exceptions.ConflictException;
import com.groupnine.bumberoos.events.IncidentReportedEvent; // <-- BAGO
import com.groupnine.bumberoos.repositories.IncidentRepository;
import org.springframework.context.ApplicationEventPublisher; // <-- BAGO
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Plain incident CRUD. Assigning staff/items, resolving, reopening and deleting live in
 * {@link DispatchService}, because they must keep staff and item availability in sync.
 */
@Service
@Transactional
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final ApplicationEventPublisher eventPublisher; // <-- BAGO

    // <-- BINAGO: Idinagdag ang eventPublisher sa constructor
    public IncidentService(IncidentRepository incidentRepository, ApplicationEventPublisher eventPublisher) {
        this.incidentRepository = incidentRepository;
        this.eventPublisher = eventPublisher;
    }

    // =====> ADD <===== //

    /** Always starts ONGOING. Records the first alarm event when an alarm level is given. */
    public IncidentEntity addIncident(IncidentEntity incident) {
        requireBothOrNeither(incident.getLatitude(), incident.getLongitude());
        incident.setId(null);
        incident.setStatus(IncidentStatus.ONGOING);
        if (incident.getAlarmLevel() != null && incident.getNature() != IncidentNature.EMERGENCY) {
            throw new BadRequestException("alarmLevel can only be set on an EMERGENCY incident");
        }
        if (incident.getAlarmLevel() != null) {
            incident.getAlarmEvents().add(new IncidentAlarmEvent(incident, incident.getAlarmLevel(), LocalDateTime.now()));
        }

        IncidentEntity savedIncident = incidentRepository.save(incident);

        // <-- BAGO: I-trigger ang event pagkatapos ma-save
        eventPublisher.publishEvent(new IncidentReportedEvent(this, savedIncident));

        return savedIncident;
    }

    // =====> EDIT <===== //

    /**
     * Null arguments leave the existing value unchanged. Status is deliberately not editable here.
     *
     * @throws ConflictException   when changing nature/alarm level of a RESOLVED incident
     * @throws BadRequestException when an alarm level is supplied for a NON_EMERGENCY result
     */
    @SuppressWarnings("deprecation")
    public Optional<IncidentEntity> editIncident(
            Long id,
            IncidentNature nature,
            AlarmLevel alarmLevel,
            String incidentType,
            String location,
            LocalDateTime dateTimeReported,
            String reportedBy,
            LocalDateTime underControlAt,
            LocalDateTime fireOutAt,
            IncidentSeverityLevel severityLevel
    ) {
        return editIncident(id, nature, alarmLevel, incidentType, location, dateTimeReported, reportedBy,
                underControlAt, fireOutAt, severityLevel, null, null);
    }

    /**
     * As above, plus the scene-arrival time and BFP Incident Commander. A blank {@code commanderName}
     * clears the commander.
     */
    @SuppressWarnings("deprecation")
    public Optional<IncidentEntity> editIncident(
            Long id,
            IncidentNature nature,
            AlarmLevel alarmLevel,
            String incidentType,
            String location,
            LocalDateTime dateTimeReported,
            String reportedBy,
            LocalDateTime underControlAt,
            LocalDateTime fireOutAt,
            IncidentSeverityLevel severityLevel,
            LocalDateTime arrivedAt,
            String commanderName
    ) {
        return editIncident(id, nature, alarmLevel, incidentType, location, dateTimeReported, reportedBy,
                underControlAt, fireOutAt, severityLevel, arrivedAt, commanderName, null, null);
    }

    /**
     * As above, plus the map position. Give both {@code latitude} and {@code longitude} to move the pin,
     * or neither to leave it alone.
     *
     * @throws BadRequestException when only one of the two is given
     */
    @SuppressWarnings("deprecation")
    public Optional<IncidentEntity> editIncident(
            Long id,
            IncidentNature nature,
            AlarmLevel alarmLevel,
            String incidentType,
            String location,
            LocalDateTime dateTimeReported,
            String reportedBy,
            LocalDateTime underControlAt,
            LocalDateTime fireOutAt,
            IncidentSeverityLevel severityLevel,
            LocalDateTime arrivedAt,
            String commanderName,
            Double latitude,
            Double longitude
    ) {
        requireBothOrNeither(latitude, longitude);
        return incidentRepository.findById(id).map(incident -> {
            boolean natureChanging = nature != null && nature != incident.getNature();
            boolean alarmChanging = alarmLevel != null && alarmLevel != incident.getAlarmLevel();

            if (incident.getStatus() == IncidentStatus.RESOLVED && (natureChanging || alarmChanging)) {
                throw new ConflictException("incident " + id + " is RESOLVED; reopen it to change nature or alarm level");
            }

            IncidentNature resultingNature = nature != null ? nature : incident.getNature();
            if (resultingNature == IncidentNature.NON_EMERGENCY && alarmLevel != null) {
                throw new BadRequestException("alarmLevel can only be set on an EMERGENCY incident");
            }

            if (natureChanging) {
                incident.setNature(nature);
                if (nature == IncidentNature.NON_EMERGENCY) incident.setAlarmLevel(null);
            }
            if (alarmChanging) {
                incident.setAlarmLevel(alarmLevel);
                incident.getAlarmEvents().add(new IncidentAlarmEvent(incident, alarmLevel, LocalDateTime.now()));
            }
            if (incidentType != null) incident.setIncidentType(incidentType);
            if (location != null) incident.setLocation(location);
            if (dateTimeReported != null) incident.setDateTimeReported(dateTimeReported);
            if (reportedBy != null) incident.setReportedBy(reportedBy);
            if (underControlAt != null) incident.setUnderControlAt(underControlAt);
            if (fireOutAt != null) incident.setFireOutAt(fireOutAt);
            if (severityLevel != null) incident.setSeverityLevel(severityLevel);
            if (arrivedAt != null) incident.setArrivedAt(arrivedAt);
            if (commanderName != null) incident.setCommanderName(commanderName);
            if (latitude != null) {
                incident.setLatitude(latitude);
                incident.setLongitude(longitude);
            }
            incident.normalize();
            return incidentRepository.save(incident);
        });
    }

    // =====> COMMANDER ORDERS <===== //

    /**
     * Records an instruction from the BFP Incident Commander, stamped with the current time.
     *
     * @throws ConflictException when the incident is RESOLVED
     */
    public Optional<IncidentEntity> addCommanderOrder(Long id, String instruction) {
        if (instruction == null || instruction.isBlank()) {
            throw new BadRequestException("instruction must not be blank");
        }
        return incidentRepository.findByIdForUpdate(id).map(incident -> {
            if (incident.getStatus() == IncidentStatus.RESOLVED) {
                throw new ConflictException("incident " + id + " is RESOLVED; reopen it to record new orders");
            }
            incident.getCommanderOrders().add(
                    new IncidentCommanderOrder(incident, instruction.trim(), LocalDateTime.now()));
            return incidentRepository.save(incident);
        });
    }

    // =====> QUERIES <===== //

    @Transactional(readOnly = true)
    public Optional<IncidentEntity> findById(Long id) {
        return incidentRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<IncidentEntity> getAllIncidents() {
        return incidentRepository.findAll();
    }

    /** Both filters are optional. */
    @Transactional(readOnly = true)
    public List<IncidentEntity> getIncidents(IncidentStatus status, IncidentNature nature) {
        if (status != null && nature != null) return incidentRepository.findByStatusAndNature(status, nature);
        if (status != null) return incidentRepository.findByStatus(status);
        if (nature != null) return incidentRepository.findByNature(nature);
        return incidentRepository.findAll();
    }

    private static void requireBothOrNeither(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new BadRequestException("latitude and longitude must be given together");
        }
    }
}