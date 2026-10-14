package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.*;
import com.groupnine.bumberoos.domain.enums.*;
import com.groupnine.bumberoos.domain.exceptions.BadRequestException;
import com.groupnine.bumberoos.domain.exceptions.ConflictException;
import com.groupnine.bumberoos.domain.exceptions.ResourceNotFoundException;
import com.groupnine.bumberoos.repositories.IncidentItemAssignmentRepository;
import com.groupnine.bumberoos.repositories.IncidentStaffAssignmentRepository;
import com.groupnine.bumberoos.services.DispatchService.ItemRequestLine;
import com.groupnine.bumberoos.services.DispatchService.ItemReturnLine;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Integration tests for DispatchService on H2. Each test rolls back. */
@SpringBootTest
@Transactional
class DispatchServiceTest {

    @Autowired DispatchService dispatch;
    @Autowired StaffService staffService;
    @Autowired InventoryService inventoryService;
    @Autowired IncidentStaffAssignmentRepository staffAssignments;
    @Autowired IncidentItemAssignmentRepository itemAssignments;
    @Autowired EntityManager em;

    // ===== helpers ===== //

    private StaffEntity staff(String name) {
        return staff(name, true, StaffAvailability.AVAILABLE);
    }

    private StaffEntity staff(String name, boolean employed, StaffAvailability availability) {
        StaffEntity s = new StaffEntity(name, "volunteer", null, null, employed, new ArrayList<>(), new ArrayList<>());
        s.setStaffAvailability(availability);
        return staffService.addStaff(s);
    }

    private ItemEntity item(String name, int quantity) throws Exception {
        return item(name, quantity, ItemCondition.GOOD);
    }

    private ItemEntity item(String name, int quantity, ItemCondition condition) throws Exception {
        return inventoryService.addItem(new ItemEntity(condition, name, null, quantity, ItemCategory.OTHER, null));
    }

    private IncidentEntity newIncident() {
        return new IncidentEntity(IncidentNature.EMERGENCY, "house fire", "zone 3", LocalDateTime.now(), "juan");
    }

    private IncidentEntity createIncident() {
        return dispatch.createIncident(newIncident(), null, null);
    }

    private void reload() {
        em.flush();
        em.clear();
    }

    private List<Long> activeStaffIds(IncidentEntity incident) {
        return staffAssignments.findByIncidentIdAndReleasedAtIsNull(incident.getId()).stream()
                .map(a -> a.getStaff().getId()).toList();
    }

    private ConflictException conflict(Runnable r) {
        return assertThrows(ConflictException.class, r::run);
    }

    // ===== create ===== //

    @Test
    void create_withStaffAndItems_dispatchesEverything() throws Exception {
        StaffEntity a = staff("ana");
        StaffEntity b = staff("ben");
        ItemEntity engine = item("engine 1", 1);
        ItemEntity suits = item("fire suit", 10);

        IncidentEntity incident = dispatch.createIncident(newIncident(),
                List.of(a.getId(), b.getId()),
                List.of(new ItemRequestLine(engine.getId(), 1), new ItemRequestLine(suits.getId(), 4)));
        reload();

        IncidentEntity loaded = em.find(IncidentEntity.class, incident.getId());
        assertEquals(IncidentStatus.ONGOING, loaded.getStatus());
        assertEquals(2, staffAssignments.findByIncidentIdAndReleasedAtIsNull(loaded.getId()).size());
        assertEquals(2, itemAssignments.findByIncidentIdAndReleasedAtIsNull(loaded.getId()).size());
        assertEquals(StaffAvailability.DISPATCHED, em.find(StaffEntity.class, a.getId()).getStaffAvailability());
        assertEquals(StaffAvailability.DISPATCHED, em.find(StaffEntity.class, b.getId()).getStaffAvailability());
        assertEquals(1, em.find(ItemEntity.class, engine.getId()).getQuantityDispatched());
        assertEquals(4, em.find(ItemEntity.class, suits.getId()).getQuantityDispatched());
        assertEquals(ItemAvailability.DISPATCHED, em.find(ItemEntity.class, engine.getId()).getAvailability());
        assertEquals(ItemAvailability.PARTIALLY_DISPATCHED, em.find(ItemEntity.class, suits.getId()).getAvailability());
    }

    @Test
    void create_defaultsReportTimeAndRecordsInitialAlarm() {
        IncidentEntity input = new IncidentEntity(IncidentNature.EMERGENCY, "fire", "z", null, "j");
        input.setAlarmLevel(AlarmLevel.FIRST_ALARM);

        IncidentEntity created = dispatch.createIncident(input, null, null);

        assertNotNull(created.getDateTimeReported());
        assertEquals(1, created.getAlarmEvents().size());
    }

    @Test
    void create_alarmOnNonEmergencyIsRejected() {
        IncidentEntity input = new IncidentEntity(IncidentNature.NON_EMERGENCY, "standby", "z", null, "j");
        input.setAlarmLevel(AlarmLevel.FIRST_ALARM);

        assertThrows(BadRequestException.class, () -> dispatch.createIncident(input, null, null));
    }

    @Test
    void create_mergesDuplicateStaffIdsAndItemLines() throws Exception {
        StaffEntity a = staff("ana");
        ItemEntity suits = item("fire suit", 10);

        IncidentEntity incident = dispatch.createIncident(newIncident(),
                List.of(a.getId(), a.getId()),
                List.of(new ItemRequestLine(suits.getId(), 2), new ItemRequestLine(suits.getId(), 3)));
        reload();

        assertEquals(1, staffAssignments.findByIncidentIdAndReleasedAtIsNull(incident.getId()).size());
        var items = itemAssignments.findByIncidentIdAndReleasedAtIsNull(incident.getId());
        assertEquals(1, items.size());
        assertEquals(5, items.get(0).getQuantity());
        assertEquals(5, em.find(ItemEntity.class, suits.getId()).getQuantityDispatched());
    }

    @Test
    void create_rejectsBadItemLines() throws Exception {
        ItemEntity suits = item("fire suit", 10);

        assertThrows(BadRequestException.class, () -> dispatch.createIncident(newIncident(), null,
                List.of(new ItemRequestLine(suits.getId(), 0))));
    }

    // ===== assignStaff / releaseStaff ===== //

    @Test
    void assignStaff_dispatchesAndIsIdempotentWithRoleUpdate() {
        IncidentEntity incident = createIncident();
        StaffEntity a = staff("ana");

        dispatch.assignStaff(incident.getId(), a.getId(), null);
        dispatch.assignStaff(incident.getId(), a.getId(), "driver");
        reload();

        var active = staffAssignments.findByIncidentIdAndReleasedAtIsNull(incident.getId());
        assertEquals(1, active.size());
        assertEquals("Driver", active.get(0).getRole());
        assertEquals(StaffAvailability.DISPATCHED, em.find(StaffEntity.class, a.getId()).getStaffAvailability());
    }

    @Test
    void assignStaff_rejectsDispatchedUnavailableAndUnemployedStaff() {
        IncidentEntity incident = createIncident();
        StaffEntity busy = staff("busy bee", true, StaffAvailability.DISPATCHED);
        StaffEntity off = staff("off duty", true, StaffAvailability.UNAVAILABLE);
        StaffEntity fired = staff("former member", false, StaffAvailability.AVAILABLE);

        assertTrue(conflict(() -> dispatch.assignStaff(incident.getId(), busy.getId(), null))
                .getMessage().contains("Busy Bee is DISPATCHED"));
        assertTrue(conflict(() -> dispatch.assignStaff(incident.getId(), off.getId(), null))
                .getMessage().contains("UNAVAILABLE"));
        assertTrue(conflict(() -> dispatch.assignStaff(incident.getId(), fired.getId(), null))
                .getMessage().contains("not employed"));
    }

    @Test
    void assignStaff_unknownIncidentOrStaffIs404() {
        IncidentEntity incident = createIncident();
        StaffEntity a = staff("ana");

        assertThrows(ResourceNotFoundException.class, () -> dispatch.assignStaff(-1L, a.getId(), null));
        assertThrows(ResourceNotFoundException.class, () -> dispatch.assignStaff(incident.getId(), -1L, null));
    }

    @Test
    void assignStaff_onResolvedIncidentIsConflict() {
        IncidentEntity incident = createIncident();
        StaffEntity a = staff("ana");
        dispatch.resolve(incident.getId(), null);

        assertTrue(conflict(() -> dispatch.assignStaff(incident.getId(), a.getId(), null))
                .getMessage().contains("RESOLVED"));
    }

    @Test
    void assignStaff_cannotBeActiveOnTwoIncidents() {
        IncidentEntity first = createIncident();
        IncidentEntity second = createIncident();
        StaffEntity a = staff("ana");

        dispatch.assignStaff(first.getId(), a.getId(), null);

        conflict(() -> dispatch.assignStaff(second.getId(), a.getId(), null));
    }

    @Test
    void releaseStaff_makesAvailableKeepsHistoryAndSecondCallIsNoOp() {
        IncidentEntity incident = createIncident();
        StaffEntity a = staff("ana");
        dispatch.assignStaff(incident.getId(), a.getId(), null);

        dispatch.releaseStaff(incident.getId(), a.getId());
        dispatch.releaseStaff(incident.getId(), a.getId());
        reload();

        assertEquals(StaffAvailability.AVAILABLE, em.find(StaffEntity.class, a.getId()).getStaffAvailability());
        assertTrue(activeStaffIds(incident).isEmpty());
        IncidentEntity loaded = em.find(IncidentEntity.class, incident.getId());
        assertEquals(1, loaded.getStaffAssignments().size(), "history is kept");
        assertNotNull(loaded.getStaffAssignments().get(0).getReleasedAt());
    }

    @Test
    void releasedStaffCanBeSentToAnotherIncident() {
        IncidentEntity first = createIncident();
        IncidentEntity second = createIncident();
        StaffEntity a = staff("ana");
        dispatch.assignStaff(first.getId(), a.getId(), null);
        dispatch.releaseStaff(first.getId(), a.getId());

        dispatch.assignStaff(second.getId(), a.getId(), null);

        assertEquals(List.of(a.getId()), activeStaffIds(second));
    }

    // ===== items ===== //

    @Test
    void setItemQuantity_createIncreaseDecreaseAndNoOp() throws Exception {
        IncidentEntity incident = createIncident();
        ItemEntity suits = item("fire suit", 10);

        dispatch.setItemQuantity(incident.getId(), suits.getId(), 2);
        assertEquals(2, suits.getQuantityDispatched());

        dispatch.setItemQuantity(incident.getId(), suits.getId(), 5);
        assertEquals(5, suits.getQuantityDispatched());

        dispatch.setItemQuantity(incident.getId(), suits.getId(), 3);
        assertEquals(3, suits.getQuantityDispatched());

        dispatch.setItemQuantity(incident.getId(), suits.getId(), 3);
        assertEquals(3, suits.getQuantityDispatched());

        var active = itemAssignments.findByIncidentIdAndReleasedAtIsNull(incident.getId());
        assertEquals(1, active.size());
        assertEquals(3, active.get(0).getQuantity());
    }

    @Test
    void setItemQuantity_rejectsMoreThanAvailable() throws Exception {
        IncidentEntity incident = createIncident();
        IncidentEntity other = createIncident();
        ItemEntity suits = item("fire suit", 10);
        dispatch.setItemQuantity(other.getId(), suits.getId(), 7);

        ConflictException create = conflict(() -> dispatch.setItemQuantity(incident.getId(), suits.getId(), 4));
        assertTrue(create.getMessage().contains("only 3"));

        dispatch.setItemQuantity(incident.getId(), suits.getId(), 3);
        conflict(() -> dispatch.setItemQuantity(incident.getId(), suits.getId(), 4)); // delta 1, none left
        assertEquals(10, suits.getQuantityDispatched());
    }

    @Test
    void setItemQuantity_rejectsNonGoodItemAndBadQuantity() throws Exception {
        IncidentEntity incident = createIncident();
        ItemEntity broken = item("broken hose", 3, ItemCondition.DAMAGED);
        ItemEntity good = item("good hose", 3);

        assertTrue(conflict(() -> dispatch.setItemQuantity(incident.getId(), broken.getId(), 1))
                .getMessage().contains("DAMAGED"));
        assertThrows(BadRequestException.class, () -> dispatch.setItemQuantity(incident.getId(), good.getId(), 0));
        assertThrows(ResourceNotFoundException.class, () -> dispatch.setItemQuantity(incident.getId(), -1L, 1));
    }

    @Test
    void setItemQuantity_onResolvedIncidentIsConflict() throws Exception {
        IncidentEntity incident = createIncident();
        ItemEntity suits = item("fire suit", 10);
        dispatch.resolve(incident.getId(), null);

        conflict(() -> dispatch.setItemQuantity(incident.getId(), suits.getId(), 1));
    }

    @Test
    void releaseItem_returnsAllUnitsAndSecondCallIsNoOp() throws Exception {
        IncidentEntity incident = createIncident();
        ItemEntity suits = item("fire suit", 10);
        dispatch.setItemQuantity(incident.getId(), suits.getId(), 6);

        dispatch.releaseItem(incident.getId(), suits.getId());
        dispatch.releaseItem(incident.getId(), suits.getId());

        assertEquals(0, suits.getQuantityDispatched());
        assertTrue(itemAssignments.findByIncidentIdAndReleasedAtIsNull(incident.getId()).isEmpty());
        reload();
        assertEquals(1, em.find(IncidentEntity.class, incident.getId()).getItemAssignments().size());
    }

    @Test
    void releasedItemCanBeSetAgainAsFreshAssignment() throws Exception {
        IncidentEntity incident = createIncident();
        ItemEntity suits = item("fire suit", 10);
        dispatch.setItemQuantity(incident.getId(), suits.getId(), 6);
        dispatch.releaseItem(incident.getId(), suits.getId());

        dispatch.setItemQuantity(incident.getId(), suits.getId(), 2);
        reload();

        assertEquals(2, em.find(ItemEntity.class, suits.getId()).getQuantityDispatched());
        assertEquals(2, em.find(IncidentEntity.class, incident.getId()).getItemAssignments().size());
    }

    // ===== resolve / reopen / delete ===== //

    @Test
    void resolve_releasesEverythingAndSetsStatus() throws Exception {
        StaffEntity a = staff("ana");
        StaffEntity b = staff("ben");
        ItemEntity engine = item("engine 1", 1);
        ItemEntity suits = item("fire suit", 10);
        IncidentEntity incident = dispatch.createIncident(newIncident(), List.of(a.getId(), b.getId()),
                List.of(new ItemRequestLine(engine.getId(), 1), new ItemRequestLine(suits.getId(), 4)));

        dispatch.resolve(incident.getId(), null);
        reload();

        IncidentEntity loaded = em.find(IncidentEntity.class, incident.getId());
        assertEquals(IncidentStatus.RESOLVED, loaded.getStatus());
        assertNotNull(loaded.getResolvedAt());
        assertEquals(StaffAvailability.AVAILABLE, em.find(StaffEntity.class, a.getId()).getStaffAvailability());
        assertEquals(StaffAvailability.AVAILABLE, em.find(StaffEntity.class, b.getId()).getStaffAvailability());
        assertEquals(0, em.find(ItemEntity.class, engine.getId()).getQuantityDispatched());
        assertEquals(0, em.find(ItemEntity.class, suits.getId()).getQuantityDispatched());
        assertTrue(loaded.getStaffAssignments().stream().noneMatch(IncidentStaffAssignment::isActive));
        assertTrue(loaded.getItemAssignments().stream().noneMatch(IncidentItemAssignment::isActive));
    }

    @Test
    void resolve_appliesReturnConditionAndDamagedItemBecomesUnavailable() throws Exception {
        ItemEntity engine = item("engine 1", 1);
        ItemEntity hose = item("hose", 5);
        IncidentEntity incident = dispatch.createIncident(newIncident(), null,
                List.of(new ItemRequestLine(engine.getId(), 1), new ItemRequestLine(hose.getId(), 2)));

        dispatch.resolve(incident.getId(), List.of(new ItemReturnLine(engine.getId(), ItemCondition.DAMAGED)));
        reload();

        ItemEntity loadedEngine = em.find(ItemEntity.class, engine.getId());
        assertEquals(ItemCondition.DAMAGED, loadedEngine.getItemCondition());
        assertEquals(ItemAvailability.UNAVAILABLE, loadedEngine.getAvailability());
        assertEquals(ItemCondition.GOOD, em.find(ItemEntity.class, hose.getId()).getItemCondition());
        var assignments = em.find(IncidentEntity.class, incident.getId()).getItemAssignments();
        assertEquals(1, assignments.stream().filter(x -> x.getReturnCondition() == ItemCondition.DAMAGED).count());
    }

    @Test
    void resolve_secondCallIsNoOp() {
        StaffEntity a = staff("ana");
        IncidentEntity incident = dispatch.createIncident(newIncident(), List.of(a.getId()), null);
        dispatch.resolve(incident.getId(), null);
        LocalDateTime firstResolvedAt = incident.getResolvedAt();

        IncidentEntity again = dispatch.resolve(incident.getId(), null);

        assertEquals(IncidentStatus.RESOLVED, again.getStatus());
        assertEquals(firstResolvedAt, again.getResolvedAt());
    }

    @Test
    void resolve_rejectsReturnForItemNotOnIncident() throws Exception {
        IncidentEntity incident = createIncident();
        ItemEntity other = item("not here", 1);

        assertThrows(BadRequestException.class, () -> dispatch.resolve(incident.getId(),
                List.of(new ItemReturnLine(other.getId(), ItemCondition.DAMAGED))));
        assertEquals(IncidentStatus.ONGOING, incident.getStatus().equals(IncidentStatus.ONGOING)
                ? IncidentStatus.ONGOING : IncidentStatus.RESOLVED);
    }

    @Test
    void resolve_unknownIncidentIs404() {
        assertThrows(ResourceNotFoundException.class, () -> dispatch.resolve(-1L, null));
    }

    @Test
    void resolve_onlyReturnsThisIncidentsUnitsOfASharedItem() throws Exception {
        IncidentEntity a = createIncident();
        IncidentEntity b = createIncident();
        ItemEntity suits = item("fire suit", 10);
        dispatch.setItemQuantity(a.getId(), suits.getId(), 4);
        dispatch.setItemQuantity(b.getId(), suits.getId(), 3);

        dispatch.resolve(a.getId(), null);

        assertEquals(3, suits.getQuantityDispatched());
        assertEquals(3, itemAssignments.findByIncidentIdAndReleasedAtIsNull(b.getId()).get(0).getQuantity());
    }

    @Test
    void reopen_doesNotRedispatchAndOngoingIsNoOp() {
        StaffEntity a = staff("ana");
        IncidentEntity incident = dispatch.createIncident(newIncident(), List.of(a.getId()), null);
        dispatch.resolve(incident.getId(), null);

        IncidentEntity reopened = dispatch.reopen(incident.getId());

        assertEquals(IncidentStatus.ONGOING, reopened.getStatus());
        assertNull(reopened.getResolvedAt());
        assertTrue(activeStaffIds(incident).isEmpty());
        assertEquals(StaffAvailability.AVAILABLE, a.getStaffAvailability());
        assertEquals(IncidentStatus.ONGOING, dispatch.reopen(incident.getId()).getStatus());
    }

    @Test
    void reopen_unknownIncidentIs404() {
        assertThrows(ResourceNotFoundException.class, () -> dispatch.reopen(-1L));
    }

    @Test
    void deleteIncident_releasesOngoingAssignmentsFirst() throws Exception {
        StaffEntity a = staff("ana");
        ItemEntity suits = item("fire suit", 10);
        IncidentEntity incident = dispatch.createIncident(newIncident(), List.of(a.getId()),
                List.of(new ItemRequestLine(suits.getId(), 4)));

        assertTrue(dispatch.deleteIncident(incident.getId()));
        reload();

        assertNull(em.find(IncidentEntity.class, incident.getId()));
        assertEquals(StaffAvailability.AVAILABLE, em.find(StaffEntity.class, a.getId()).getStaffAvailability());
        assertEquals(0, em.find(ItemEntity.class, suits.getId()).getQuantityDispatched());
        assertFalse(dispatch.deleteIncident(incident.getId()));
    }

    @Test
    void deleteIncident_resolvedDoesNotTouchStaffNowOnAnotherIncident() {
        StaffEntity a = staff("ana");
        IncidentEntity first = dispatch.createIncident(newIncident(), List.of(a.getId()), null);
        dispatch.resolve(first.getId(), null);
        IncidentEntity second = createIncident();
        dispatch.assignStaff(second.getId(), a.getId(), null);

        assertTrue(dispatch.deleteIncident(first.getId()));

        assertEquals(StaffAvailability.DISPATCHED, a.getStaffAvailability());
    }

    @Test
    void deleteIncident_unknownReturnsFalse() {
        assertFalse(dispatch.deleteIncident(-1L));
    }

    // ===== rules that depend on assignment history ===== //

    @Test
    void staffWithAssignmentHistoryCannotBeDeletedOrFlippedWhileAssigned() {
        StaffEntity a = staff("ana");
        IncidentEntity incident = dispatch.createIncident(newIncident(), List.of(a.getId()), null);

        conflict(() -> staffService.editStaff(a.getId(), null, null, null, null, null, null, null,
                StaffAvailability.AVAILABLE));
        conflict(() -> staffService.editStaff(a.getId(), null, null, null, null, false, null, null, null));
        conflict(() -> staffService.removeStaff(a.getId()));

        dispatch.resolve(incident.getId(), null);

        conflict(() -> staffService.removeStaff(a.getId())); // history remains
        assertTrue(staffService.editStaff(a.getId(), null, null, null, null, false, null, null, null).isPresent());
    }

    @Test
    void itemWithAssignmentHistoryCannotBeDeletedOrShrunkBelowDispatched() throws Exception {
        ItemEntity suits = item("fire suit", 10);
        IncidentEntity incident = createIncident();
        dispatch.setItemQuantity(incident.getId(), suits.getId(), 6);

        conflict(() -> inventoryService.editItem(suits.getId(), null, null, 5, null, null, null, null));
        conflict(() -> inventoryService.increaseQuantity(suits.getId(), -5));
        assertEquals(6, inventoryService.editItem(suits.getId(), null, null, 6, null, null, null, null)
                .orElseThrow().getQuantity());

        dispatch.resolve(incident.getId(), null);

        conflict(() -> inventoryService.removeItem(suits.getId()));
    }

    @Test
    void availableOnlyFilterExcludesFullyDispatchedAndNonGoodItems() throws Exception {
        IncidentEntity incident = createIncident();
        ItemEntity full = item("full engine", 1);
        ItemEntity free = item("free hose", 5);
        ItemEntity broken = item("broken ladder", 1, ItemCondition.DAMAGED);
        dispatch.setItemQuantity(incident.getId(), full.getId(), 1);

        var ids = inventoryService.getItems(null, true).stream().map(ItemEntity::getId).toList();

        assertTrue(ids.contains(free.getId()));
        assertFalse(ids.contains(full.getId()));
        assertFalse(ids.contains(broken.getId()));
        assertTrue(inventoryService.getItems(ItemCategory.ENGINE, true).isEmpty()
                || inventoryService.getItems(ItemCategory.ENGINE, true).stream()
                        .allMatch(i -> i.getCategory() == ItemCategory.ENGINE));
    }
}
