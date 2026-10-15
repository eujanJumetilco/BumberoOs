package com.groupnine.bumberoos.mappers;

import com.groupnine.bumberoos.domain.entities.*;
import com.groupnine.bumberoos.services.DispatchService;
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
                "juan dela cruz", "driver", "0917-123-4567", "pasay", true, null, List.of("first aid"), null));

        assertEquals("Juan Dela Cruz", e.getName());
        assertEquals("09171234567", e.getContactNumber());
        assertTrue(e.getTrainings().isEmpty());
        assertEquals(List.of("First Aid"), e.getCertifications());
    }

    @Test
    void staff_entityToResponse_copiesFields() {
        StaffEntity e = staffMapper.toEntity(new StaffRequest(
                "juan", "driver", "09171234567", "pasay", true, List.of("a"), List.of("b"), "2516528"));
        e.setId(7L);
        e.setStaffAvailability(StaffAvailability.DISPATCHED);

        StaffResponse r = staffMapper.toResponse(e);

        assertEquals(7L, r.id());
        assertEquals("Juan", r.name());
        assertEquals(StaffAvailability.DISPATCHED, r.availability());
        assertEquals(List.of("A"), r.trainings());
        assertEquals(List.of("B"), r.certifications());
        assertEquals("2516528", r.badgeNumber());
    }

    @Test
    void staff_responseListsAreDefensiveCopies() {
        StaffEntity e = staffMapper.toEntity(new StaffRequest("j", "d", null, null, true, List.of("a"), null, null));

        assertThrows(UnsupportedOperationException.class, () -> staffMapper.toResponse(e).trainings().add("x"));
    }

    // ===== Incident ===== //

    private IncidentRequest incidentRequest(IncidentNature nature, AlarmLevel alarm, LocalDateTime when) {
        return new IncidentRequest(nature, alarm, "house fire", "brgy 1", "juan", when, null, null, null);
    }

    @Test
    void incident_roundTrip() {
        LocalDateTime when = LocalDateTime.of(2026, 1, 1, 9, 0);
        IncidentEntity e = incidentMapper.toEntity(incidentRequest(IncidentNature.EMERGENCY, AlarmLevel.FIRST_ALARM, when));
        e.setId(3L);

        IncidentResponse r = incidentMapper.toResponse(e);

        assertEquals(3L, r.id());
        assertEquals(IncidentNature.EMERGENCY, r.nature());
        assertEquals(IncidentStatus.ONGOING, r.status());
        assertEquals(AlarmLevel.FIRST_ALARM, r.alarmLevel());
        assertEquals("House Fire", r.incidentType());
        assertEquals(when, r.dateTimeReported());
        assertNull(r.severityLevel());
        assertTrue(r.deployedStaffIds().isEmpty());
        assertTrue(r.staff().isEmpty());
        assertTrue(r.items().isEmpty());
        assertTrue(r.alarmHistory().isEmpty());
    }

    @Test
    void incident_missingReportTimeDefaultsToNow() {
        IncidentEntity e = incidentMapper.toEntity(incidentRequest(IncidentNature.NON_EMERGENCY, null, null));

        assertNotNull(e.getDateTimeReported());
    }

    @Test
    void incident_response_activeStaffIdsVersusFullHistory() {
        IncidentEntity incident = incidentMapper.toEntity(incidentRequest(IncidentNature.EMERGENCY, null, null));
        incident.setId(1L);
        StaffEntity active = staff(2L, "ana reyes");
        StaffEntity gone = staff(5L, "ben cruz");
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 9, 0);
        IncidentStaffAssignment a1 = new IncidentStaffAssignment(incident, active, "driver", t);
        IncidentStaffAssignment a2 = new IncidentStaffAssignment(incident, gone, null, t);
        a2.setReleasedAt(t.plusHours(1));
        incident.getStaffAssignments().add(a1);
        incident.getStaffAssignments().add(a2);

        IncidentResponse r = incidentMapper.toResponse(incident);

        assertEquals(List.of(2L), r.deployedStaffIds());
        assertEquals(2, r.staff().size());
        assertEquals("Driver", r.staff().get(0).role());
        assertEquals("Ana Reyes", r.staff().get(0).name());
        assertNull(r.staff().get(0).releasedAt());
        assertEquals(t.plusHours(1), r.staff().get(1).releasedAt());
    }

    @Test
    void incident_response_includesItemAssignmentsAndAlarmHistory() {
        IncidentEntity incident = incidentMapper.toEntity(incidentRequest(IncidentNature.EMERGENCY, null, null));
        ItemEntity engine = new ItemEntity(ItemCondition.GOOD, "engine 1", null, 1, ItemCategory.ENGINE, "ABC 123");
        engine.setId(7L);
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 9, 0);
        IncidentItemAssignment assignment = new IncidentItemAssignment(incident, engine, 1, t);
        assignment.setReleasedAt(t.plusHours(2));
        assignment.setReturnCondition(ItemCondition.DAMAGED);
        incident.getItemAssignments().add(assignment);
        incident.getAlarmEvents().add(new IncidentAlarmEvent(incident, AlarmLevel.SECOND_ALARM, t));

        IncidentResponse r = incidentMapper.toResponse(incident);

        assertEquals(1, r.items().size());
        assertEquals(7L, r.items().get(0).itemId());
        assertEquals(ItemCategory.ENGINE, r.items().get(0).category());
        assertEquals(ItemCondition.DAMAGED, r.items().get(0).returnCondition());
        assertEquals(List.of(AlarmLevel.SECOND_ALARM), r.alarmHistory().stream().map(AlarmEventResponse::alarmLevel).toList());
    }

    @Test
    void incident_itemLinesAndReturnsAreMappedForTheService() {
        var lines = incidentMapper.toItemLines(List.of(new ItemLine(7L, 2)));
        assertEquals(new DispatchService.ItemRequestLine(7L, 2), lines.get(0));
        assertTrue(incidentMapper.toItemLines(null).isEmpty());

        var returns = incidentMapper.toItemReturns(new ResolveRequest(List.of(new ItemReturn(7L, ItemCondition.DAMAGED))));
        assertEquals(new DispatchService.ItemReturnLine(7L, ItemCondition.DAMAGED), returns.get(0));
        assertTrue(incidentMapper.toItemReturns(null).isEmpty());
    }

    private StaffEntity staff(Long id, String name) {
        StaffEntity s = new StaffEntity(name, "driver", null, null, true, null, null);
        s.setId(id);
        return s;
    }

    // ===== Item ===== //

    @Test
    void item_roundTrip() {
        ItemEntity e = itemMapper.toEntity(new ItemRequest(
                "fire hose", "30 meter", 5, ItemCondition.DAMAGED, ItemCategory.HOSE, " H-1 "));
        e.setId(2L);
        e.setLastMaintained(LocalDate.of(2026, 2, 2));

        ItemResponse r = itemMapper.toResponse(e);

        assertEquals(2L, r.id());
        assertEquals("Fire Hose", r.name());
        assertEquals(5, r.quantity());
        assertEquals(ItemCondition.DAMAGED, r.condition());
        assertEquals(LocalDate.of(2026, 2, 2), r.lastMaintained());
        assertEquals(ItemCategory.HOSE, r.category());
        assertEquals("H-1", r.serialNumber());
        assertEquals(0, r.quantityDispatched());
        assertEquals(5, r.quantityAvailable());
    }

    @Test
    void item_categoryDefaultsToOther() {
        ItemEntity e = itemMapper.toEntity(new ItemRequest("x", null, 1, ItemCondition.GOOD, null, null));

        assertEquals(ItemCategory.OTHER, e.getCategory());
    }

    @Test
    void item_derivedAvailability_allFourStates() {
        ItemEntity item = new ItemEntity(ItemCondition.GOOD, "suit", null, 10);

        assertEquals(ItemAvailability.AVAILABLE, itemMapper.toResponse(item).availability());

        item.setQuantityDispatched(4);
        ItemResponse partial = itemMapper.toResponse(item);
        assertEquals(ItemAvailability.PARTIALLY_DISPATCHED, partial.availability());
        assertEquals(6, partial.quantityAvailable());

        item.setQuantityDispatched(10);
        assertEquals(ItemAvailability.DISPATCHED, itemMapper.toResponse(item).availability());

        item.setQuantityDispatched(0);
        item.setItemCondition(ItemCondition.DAMAGED);
        assertEquals(ItemAvailability.UNAVAILABLE, itemMapper.toResponse(item).availability());

        item.setItemCondition(ItemCondition.GOOD);
        item.setQuantity(0);
        assertEquals(ItemAvailability.UNAVAILABLE, itemMapper.toResponse(item).availability());
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
        assertFalse(validator.validate(new StaffRequest(" ", "driver", null, null, true, null, null, null)).isEmpty());
        assertFalse(validator.validate(new IncidentRequest(null, null, "", "", "", null, null, null, null)).isEmpty());
        assertFalse(validator.validate(new ItemRequest("", null, -1, null, null, null)).isEmpty());
        assertFalse(validator.validate(new ItemUpdateRequest(null, null, -5, null, null, null, null)).isEmpty());
        assertFalse(validator.validate(new SignUpRequest("a@b.com", "short", null, null)).isEmpty());
        assertFalse(validator.validate(new SignInRequest("", "")).isEmpty());
        assertFalse(validator.validate(new ItemLine(1L, 0)).isEmpty());
        assertFalse(validator.validate(new ItemLine(null, 1)).isEmpty());
        assertFalse(validator.validate(new IncidentRequest(IncidentNature.EMERGENCY, null, "fire", "z3", "jc", null, null,
                null, List.of(new ItemLine(7L, 0)))).isEmpty());
        assertFalse(validator.validate(new IncidentRequest(IncidentNature.EMERGENCY, null, "fire", "z3", "jc", null, null,
                java.util.Arrays.asList(1L, null), null)).isEmpty());
        assertFalse(validator.validate(new ItemReturn(null, ItemCondition.GOOD)).isEmpty());
        assertFalse(validator.validate(new StaffRequest("juan", "driver", null, null, true, null, null, "123")).isEmpty());
        assertFalse(validator.validate(new StaffRequest("juan", "driver", null, null, true, null, null, "25165289")).isEmpty());
        assertFalse(validator.validate(new StaffRequest("juan", "driver", null, null, true, null, null, "25165a8")).isEmpty());
        assertFalse(validator.validate(new StaffUpdateRequest(null, null, null, null, null, null, null, null, "12")).isEmpty());
        assertFalse(validator.validate(new CommanderOrderRequest(" ")).isEmpty());
        assertFalse(validator.validate(new CommanderOrderRequest("x".repeat(1001))).isEmpty());
    }

    @Test
    void validation_acceptsGoodRequests() {
        assertTrue(validator.validate(new StaffRequest("juan", "driver", null, null, true, null, null, null)).isEmpty());
        assertTrue(validator.validate(new StaffRequest("juan", "driver", null, null, true, null, null, "2516528")).isEmpty());
        assertTrue(validator.validate(new StaffUpdateRequest(null, null, null, null, null, null, null, null, "")).isEmpty());
        assertTrue(validator.validate(new CommanderOrderRequest("Lay a supply line to the hydrant")).isEmpty());
        assertTrue(validator.validate(new ItemRequest("hose", null, 0, ItemCondition.GOOD, null, null)).isEmpty());
        assertTrue(validator.validate(new SignUpRequest("a@b.com", "secret123", null, null)).isEmpty());
        assertTrue(validator.validate(new StaffUpdateRequest(null, null, null, null, null, null, null, null, null)).isEmpty());
        assertTrue(validator.validate(incidentRequest(IncidentNature.EMERGENCY, null, null)).isEmpty());
        assertTrue(validator.validate(new IncidentRequest(IncidentNature.EMERGENCY, null, "fire", "z3", "jc", null, null,
                List.of(1L, 2L), List.of(new ItemLine(7L, 1)))).isEmpty());
    }
}
