package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.dto.StaffRequest;
import com.groupnine.bumberoos.dto.StaffResponse;
import com.groupnine.bumberoos.dto.StaffUpdateRequest;
import com.groupnine.bumberoos.mappers.StaffMapper;
import com.groupnine.bumberoos.services.StaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    // ------------------ DEPENDENCY SETUP ------------------ //

    private final StaffService staffService;
    private final StaffMapper staffMapper;

    public StaffController(StaffService staffService, StaffMapper staffMapper){
        this.staffService = staffService;
        this.staffMapper = staffMapper;
    }

    // ------------------ CONTROLLERS ------------------ //

    @GetMapping
    public List<StaffResponse> getAll() {
        return staffMapper.toResponses(staffService.getStaffList());
    }

    @GetMapping("/{id}")
    public StaffResponse getById(@PathVariable Long id) {
        return staffService.findById(id).map(staffMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffResponse add(@Valid @RequestBody StaffRequest request) {
        return staffMapper.toResponse(staffService.addStaff(staffMapper.toEntity(request)));
    }

    @PatchMapping("/{id}")
    public StaffResponse edit(@PathVariable Long id, @Valid @RequestBody StaffUpdateRequest r) {
        return staffService.editStaff(id, r.name(), r.role(), r.contactNumber(), r.address(),
                        r.employed(), r.trainings(), r.certifications(), r.availability())
                .map(staffMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) {
        if (!staffService.removeStaff(id)) throw notFound(id);
    }

    private ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "staff " + id + " not found");
    }
}
