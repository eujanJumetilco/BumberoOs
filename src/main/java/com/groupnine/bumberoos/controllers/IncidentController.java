package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.dto.IncidentRequest;
import com.groupnine.bumberoos.dto.IncidentResponse;
import com.groupnine.bumberoos.dto.IncidentUpdateRequest;
import com.groupnine.bumberoos.mappers.IncidentMapper;
import com.groupnine.bumberoos.services.IncidentService;
import com.groupnine.bumberoos.services.StaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    // ------------------ DEPENDENCY SETUP ------------------ //

    private final IncidentService incidentService;
    private final StaffService staffService;
    private final IncidentMapper incidentMapper;

    public IncidentController(
            IncidentService incidentService,
            StaffService staffService,
            IncidentMapper incidentMapper
    ){
        this.incidentService = incidentService;
        this.staffService = staffService;
        this.incidentMapper = incidentMapper;
    }

    // ------------------ CONTROLLERS ------------------ //

    @GetMapping
    public List<IncidentResponse> getAll() {
        return incidentMapper.toResponses(incidentService.getAllIncidents());
    }

    @GetMapping("/{id}")
    public IncidentResponse getById(@PathVariable Long id) {
        return incidentService.findById(id).map(incidentMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentResponse add(@Valid @RequestBody IncidentRequest request) {
        return incidentMapper.toResponse(incidentService.addIncident(incidentMapper.toEntity(request)));
    }

    @PatchMapping("/{id}")
    public IncidentResponse edit(@PathVariable Long id, @Valid @RequestBody IncidentUpdateRequest r) {
        return incidentService.editIncident(id, r.severityLevel(), r.status(), r.incidentType(),
                        r.location(), r.dateTimeReported(), r.reportedBy())
                .map(incidentMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    @PutMapping("/{id}/staff/{staffId}")
    public IncidentResponse deployStaff(@PathVariable Long id, @PathVariable Long staffId) {
        var staff = staffService.findById(staffId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "staff " + staffId + " not found"));
        return incidentService.deployStaff(id, staff).map(incidentMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    @DeleteMapping("/{id}/staff/{staffId}")
    public IncidentResponse undeployStaff(@PathVariable Long id, @PathVariable Long staffId) {
        return incidentService.undeployStaff(id, staffId).map(incidentMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) {
        if (!incidentService.removeIncident(id)) throw notFound(id);
    }

    private ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "incident " + id + " not found");
    }
}
