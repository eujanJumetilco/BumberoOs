package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.IncidentEntity;
import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.domain.enums.IncidentSeverityLevel;
import com.groupnine.bumberoos.domain.enums.IncidentStatus;
import com.groupnine.bumberoos.repositories.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class IncidentService {

    private final IncidentRepository incidentRepository;

    public IncidentService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    // =====> ADD <===== //

    public IncidentEntity addIncident(IncidentEntity incident) {
        incident.setId(null);
        return incidentRepository.save(incident);
    }

    // =====> REMOVE <===== //

    /** @return true if found and removed, false otherwise. */
    public boolean removeIncident(Long id) {
        if (!incidentRepository.existsById(id)) return false;
        incidentRepository.deleteById(id);
        return true;
    }

    // =====> EDIT <===== //

    /** Null arguments leave the existing value unchanged. */
    public Optional<IncidentEntity> editIncident(
            Long id,
            IncidentSeverityLevel severityLevel,
            IncidentStatus status,
            String incidentType,
            String location,
            LocalDateTime dateTimeReported,
            String reportedBy
    ) {
        return incidentRepository.findById(id).map(incident -> {
            if (severityLevel != null) incident.setSeverityLevel(severityLevel);
            if (status != null) incident.setStatus(status);
            if (incidentType != null) incident.setIncidentType(incidentType);
            if (location != null) incident.setLocation(location);
            if (dateTimeReported != null) incident.setDateTimeReported(dateTimeReported);
            if (reportedBy != null) incident.setReportedBy(reportedBy);
            incident.normalize();
            return incidentRepository.save(incident);
        });
    }

    // =====> STAFF DEPLOYMENT <===== //

    /** Deploying an already-deployed staff member is a no-op. */
    public Optional<IncidentEntity> deployStaff(Long incidentId, StaffEntity staff) {
        return incidentRepository.findById(incidentId).map(incident -> {
            boolean alreadyDeployed = incident.getDeployedStaff().stream()
                    .anyMatch(s -> s.getId().equals(staff.getId()));
            if (!alreadyDeployed) incident.getDeployedStaff().add(staff);
            return incidentRepository.save(incident);
        });
    }

    public Optional<IncidentEntity> undeployStaff(Long incidentId, Long staffId) {
        return incidentRepository.findById(incidentId).map(incident -> {
            incident.getDeployedStaff().removeIf(s -> s.getId().equals(staffId));
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
}
