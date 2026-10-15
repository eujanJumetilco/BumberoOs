package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.domain.enums.IncidentNature;
import com.groupnine.bumberoos.domain.enums.IncidentStatus;
import com.groupnine.bumberoos.dto.CommanderOrderRequest;
import com.groupnine.bumberoos.dto.IncidentRequest;
import com.groupnine.bumberoos.dto.IncidentResponse;
import com.groupnine.bumberoos.dto.IncidentUpdateRequest;
import com.groupnine.bumberoos.dto.ResolveRequest;
import com.groupnine.bumberoos.mappers.IncidentMapper;
import com.groupnine.bumberoos.services.DispatchService;
import com.groupnine.bumberoos.services.IncidentService;
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
    private final DispatchService dispatchService;
    private final IncidentMapper incidentMapper;

    public IncidentController(
            IncidentService incidentService,
            DispatchService dispatchService,
            IncidentMapper incidentMapper
    ){
        this.incidentService = incidentService;
        this.dispatchService = dispatchService;
        this.incidentMapper = incidentMapper;
    }

    // ------------------ CONTROLLERS ------------------ //

    @GetMapping
    public List<IncidentResponse> getAll(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentNature nature
    ) {
        return incidentMapper.toResponses(incidentService.getIncidents(status, nature));
    }

    @GetMapping("/{id}")
    public IncidentResponse getById(@PathVariable Long id) {
        return incidentService.findById(id).map(incidentMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    /** Creates the incident and assigns any requested staff/items atomically. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentResponse add(@Valid @RequestBody IncidentRequest request) {
        return incidentMapper.toResponse(dispatchService.createIncident(
                incidentMapper.toEntity(request),
                request.staffIds(),
                incidentMapper.toItemLines(request.items())));
    }

    @PatchMapping("/{id}")
    public IncidentResponse edit(@PathVariable Long id, @Valid @RequestBody IncidentUpdateRequest r) {
        return incidentService.editIncident(id, r.nature(), r.alarmLevel(), r.incidentType(), r.location(),
                        r.dateTimeReported(), r.reportedBy(), r.underControlAt(), r.fireOutAt(), r.severityLevel(),
                        r.arrivedAt(), r.commanderName())
                .map(incidentMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    /** Records an instruction from the BFP Incident Commander. */
    @PostMapping("/{id}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentResponse addCommanderOrder(@PathVariable Long id, @Valid @RequestBody CommanderOrderRequest request) {
        return incidentService.addCommanderOrder(id, request.instruction())
                .map(incidentMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    @PutMapping("/{id}/staff/{staffId}")
    public IncidentResponse assignStaff(
            @PathVariable Long id, @PathVariable Long staffId,
            @RequestParam(required = false) String role
    ) {
        return incidentMapper.toResponse(dispatchService.assignStaff(id, staffId, role));
    }

    @DeleteMapping("/{id}/staff/{staffId}")
    public IncidentResponse releaseStaff(@PathVariable Long id, @PathVariable Long staffId) {
        return incidentMapper.toResponse(dispatchService.releaseStaff(id, staffId));
    }

    @PutMapping("/{id}/items/{itemId}")
    public IncidentResponse setItemQuantity(
            @PathVariable Long id, @PathVariable Long itemId, @RequestParam int quantity
    ) {
        return incidentMapper.toResponse(dispatchService.setItemQuantity(id, itemId, quantity));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public IncidentResponse releaseItem(@PathVariable Long id, @PathVariable Long itemId) {
        return incidentMapper.toResponse(dispatchService.releaseItem(id, itemId));
    }

    @PostMapping("/{id}/resolve")
    public IncidentResponse resolve(
            @PathVariable Long id, @Valid @RequestBody(required = false) ResolveRequest request
    ) {
        return incidentMapper.toResponse(dispatchService.resolve(id, incidentMapper.toItemReturns(request)));
    }

    @PostMapping("/{id}/reopen")
    public IncidentResponse reopen(@PathVariable Long id) {
        return incidentMapper.toResponse(dispatchService.reopen(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) {
        if (!dispatchService.deleteIncident(id)) throw notFound(id);
    }

    private ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "incident " + id + " not found");
    }
}
