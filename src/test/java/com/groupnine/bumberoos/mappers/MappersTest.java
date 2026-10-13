package com.groupnine.bumberoos.mappers;

import com.groupnine.bumberoos.domain.entities.IncidentEntity;
import com.groupnine.bumberoos.domain.entities.ItemEntity;
import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.domain.entities.UserAccountEntity;
import com.groupnine.bumberoos.domain.enums.*;
import com.groupnine.bumberoos.dto.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MappersTest {

    private final StaffMapper staffMapper = new StaffMapper();
    private final IncidentMapper incidentMapper = new IncidentMapper();
    private final ItemMapper itemMapper = new ItemMapper();
    private final UserAccountMapper accountMapper = new UserAccountMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    // ===== Staff ===== //

    @Test
    void staff_requestToEntity_normalizesAndHandlesNullLists() {
        StaffEntity e = staffMapper.toEntity(new StaffRequest(
                "juan dela cruz", "driver", "0917-123-4567", "pasay", true, null, List.of("first aid")));

        assertEquals("Juan Dela Cruz", e.getName());
        assertEquals("09171234567", e.getContactNumber());
        assertTrue(e.getTrainings().isEmpty());
        assertEquals(List.of("First Aid"), e.getCertifications());
    }

    @Test
    void staff_entityToResponse_copiesFields() {
        StaffEntity e = staffMapper.toEntity(new StaffRequest(
                "juan", "driver", "09171234567", "pasay", true, List.of("a"), List.of("b")));
        e.setId(7L);
        e.setStaffAvailability(StaffAvailability.DISPATCHED);

        StaffResponse r = staffMapper.toResponse(e);

        assertEquals(7L, r.id());
        assertEquals("Juan", r.name());
        assertEquals(StaffAvailability.DISPATCHED, r.availability());
        assertEquals(List.of("A"), r.trainings());
        assertEquals(List.of("B"), r.certifications());
    }

    @Test
    void staff_responseListsAreDefensiveCopies() {
        StaffEntity e = staffMapper.toEntity(new StaffRequest("j", "d", null, null, true, List.of("a"), null));

        assertThrows(UnsupportedOperationException.class, () -> staffMapper.toResponse(e).trainings().add("x"));
    }

    // ===== Incident ===== //

    @Test
    void incident_roundTrip() {
        LocalDateTime when = LocalDateTime.of(2026, 1, 1, 9, 0);
        IncidentEntity e = incidentMapper.toEntity(new IncidentRequest(
                IncidentSeverityLevel.HIGH, IncidentStatus.PENDING, "house fire", "brgy 1", when, "juan"));
        e.setId(3L);

        IncidentResponse r = incidentMapper.toResponse(e);

        assertEquals(3L, r.id());
        assertEquals(IncidentSeverityLevel.HIGH, r.severityLevel());
        assertEquals(IncidentStatus.PENDING, r.status());
        assertEquals("House Fire", r.incidentType());
        assertEquals(when, r.dateTimeReported());
    }

    @Test
    void incident_missingReportTimeDefaultsToNow() {
        IncidentEntity e = incidentMapper.toEntity(new IncidentRequest(
                IncidentSeverityLevel.LOW, IncidentStatus.ONGOING, "t", "l", null, "r"));

        assertNotNull(e.getDateTimeReported());
    }

    // ===== Item ===== //

    @Test
    void item_roundTrip() {
        ItemEntity e = itemMapper.toEntity(new ItemRequest("fire hose", "30 meter", 5, ItemCondition.DAMAGED));
        e.setId(2L);
        e.setLastMaintained(LocalDate.of(2026, 2, 2));

        ItemResponse r = itemMapper.toResponse(e);

        assertEquals(2L, r.id());
        assertEquals("Fire Hose", r.name());
        assertEquals(5, r.quantity());
        assertEquals(ItemCondition.DAMAGED, r.condition());
        assertEquals(LocalDate.of(2026, 2, 2), r.lastMaintained());
    }

    // ===== User account ===== //

    @Test
    void account_response_hasStaffIdWhenLinked_andNoSecretFields() {
        UserAccountEntity a = new UserAccountEntity("A@B.com", "hash", UserRole.ADMIN, "maria");
        a.setId(1L);

        assertNull(accountMapper.toResponse(a).staffId());

        StaffEntity s = new StaffEntity("juan", "driver", null, null, true, null, null);
        s.setId(4L);
        a.setStaff(s);
        UserAccountResponse r = accountMapper.toResponse(a);

        assertEquals(4L, r.staffId());
        assertEquals("a@b.com", r.email());
        assertTrue(Arrays.stream(UserAccountResponse.class.getRecordComponents())
                .noneMatch(c -> c.getName().toLowerCase().contains("password")
                        || c.getName().toLowerCase().contains("hash")));
    }

    @Test
    void authRequests_toStringMasksPassword() {
        assertFalse(new SignInRequest("a@b.com", "secret123").toString().contains("secret123"));
        assertFalse(new SignUpRequest("a@b.com", "secret123", "x", null).toString().contains("secret123"));
    }

    // ===== Validation ===== //

    @Test
    void validation_rejectsBadRequests() {
        assertFalse(validator.validate(new StaffRequest(" ", "driver", null, null, true, null, null)).isEmpty());
        assertFalse(validator.validate(new IncidentRequest(null, null, "", "", null, "")).isEmpty());
        assertFalse(validator.validate(new ItemRequest("", null, -1, null)).isEmpty());
        assertFalse(validator.validate(new ItemUpdateRequest(null, null, -5, null, null)).isEmpty());
        assertFalse(validator.validate(new SignUpRequest("a@b.com", "short", null, null)).isEmpty());
        assertFalse(validator.validate(new SignInRequest("", "")).isEmpty());
    }

    @Test
    void validation_acceptsGoodRequests() {
        assertTrue(validator.validate(new StaffRequest("juan", "driver", null, null, true, null, null)).isEmpty());
        assertTrue(validator.validate(new ItemRequest("hose", null, 0, ItemCondition.GOOD)).isEmpty());
        assertTrue(validator.validate(new SignUpRequest("a@b.com", "secret123", null, null)).isEmpty());
        assertTrue(validator.validate(new StaffUpdateRequest(null, null, null, null, null, null, null, null)).isEmpty());
    }
}
