package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.domain.enums.StaffAvailability;
import com.groupnine.bumberoos.dto.StaffRequest;
import com.groupnine.bumberoos.dto.StaffResponse;
import com.groupnine.bumberoos.dto.StaffUpdateRequest;
import com.groupnine.bumberoos.mappers.StaffMapper;
import com.groupnine.bumberoos.services.StaffService;
import com.groupnine.bumberoos.util.CsvSupport;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private static final List<String> CSV_HEADERS = List.of(
            "ID", "Staff ID", "Name", "Role", "Contact Number", "Address",
            "Employed", "Availability", "Trainings", "Certifications", "Date Joined");

    private final StaffService staffService;
    private final StaffMapper staffMapper;

    public StaffController(StaffService staffService, StaffMapper staffMapper){
        this.staffService = staffService;
        this.staffMapper = staffMapper;
    }

    @GetMapping
    public List<StaffResponse> getAll(@RequestParam(required = false) StaffAvailability availability) {
        return staffMapper.toResponses(staffService.getStaffList(availability));
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
                        r.employed(), r.trainings(), r.certifications(), r.availability(), r.badgeNumber())
                .map(staffMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    /** Soft-remove: sets employed = false and availability = UNAVAILABLE, keeping history and accounts. */
    @PatchMapping("/{id}/retire")
    public StaffResponse retire(@PathVariable Long id) {
        return staffService.retireStaff(id)
                .map(staffMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) {
        if (!staffService.removeStaff(id)) throw notFound(id);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportFile(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) StaffAvailability availability
    ) {
        List<List<Object>> rows = new ArrayList<>();
        for (StaffEntity s : staffService.getStaffList(availability)) {
            rows.add(CsvSupport.row(
                    s.getId(), s.getBadgeNumber(), CsvSupport.safe(s.getName()), CsvSupport.safe(s.getRole()),
                    CsvSupport.safe(s.getContactNumber()), CsvSupport.safe(s.getAddress()),
                    s.isEmployed() ? "Yes" : "No", CsvSupport.name(s.getStaffAvailability()),
                    CsvSupport.joinList(s.getTrainings()), CsvSupport.joinList(s.getCertifications()),
                    s.getDateJoined()));
        }
        return CsvSupport.export("staff", CsvSupport.Format.of(format), CSV_HEADERS, rows);
    }

    @PostMapping("/import")
    @Transactional(rollbackFor = Exception.class)
    public CsvSupport.ImportResult importFile(@RequestParam("file") MultipartFile file) {
        CsvSupport.Table table = CsvSupport.parse(file);
        table.require(CSV_HEADERS, "name", "role");

        Set<String> takenBadges = new HashSet<>();
        for (StaffEntity existing : staffService.getStaffList((StaffAvailability) null)) {
            if (existing.getBadgeNumber() != null) takenBadges.add(existing.getBadgeNumber());
        }

        // Explicitly typed so the compiler has nothing to infer: T = StaffEntity for both arguments.
        Function<CsvSupport.Row, StaffEntity> parser = row -> toEntity(row, takenBadges);
        CsvSupport.RowSaver<StaffEntity> saver = staffService::addStaff;

        return CsvSupport.importRows(table, parser, saver);
    }

    private StaffEntity toEntity(CsvSupport.Row row, Set<String> takenBadges) {
        String name = row.get("name");
        String role = row.get("role");
        if (name == null || role == null) throw new IllegalArgumentException("Name and role are required.");

        String badge = row.get("staff id", "badge number", "badge");
        if (badge != null) {
            if (!badge.matches("\\d{7}")) throw new IllegalArgumentException("Staff ID must be exactly 7 digits.");
            if (!takenBadges.add(badge)) throw new IllegalArgumentException("Staff ID " + badge + " is already in use.");
        }

        boolean employed = CsvSupport.parseBool(row.get("employed"), "Employed", true);

        StaffAvailability availability = CsvSupport.parseEnum(
                StaffAvailability.class, row.get("availability"), "Availability");

        // Blank -> UNAVAILABLE. DISPATCHED can't be imported (only an incident assignment sets it),
        // and anyone not employed can't be available.
        if (availability == null || availability == StaffAvailability.DISPATCHED || !employed) {
            availability = StaffAvailability.UNAVAILABLE;
        }

        StaffEntity staff = new StaffEntity(
                name, role, row.get("contact number", "contact"), row.get("address"), employed,
                CsvSupport.splitList(row.get("trainings")), CsvSupport.splitList(row.get("certifications")));
        staff.setBadgeNumber(badge);
        staff.setStaffAvailability(availability);
        staff.setDateJoined(CsvSupport.parseDate(row.get("date joined", "joined"), "Date Joined"));
        return staff;
    }

    private ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "staff " + id + " not found");
    }
}