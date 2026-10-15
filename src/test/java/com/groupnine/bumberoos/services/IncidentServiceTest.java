package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.exceptions.ConflictException;
import com.groupnine.bumberoos.domain.exceptions.BadRequestException;
import com.groupnine.bumberoos.domain.entities.IncidentEntity;
import com.groupnine.bumberoos.domain.enums.AlarmLevel;
import com.groupnine.bumberoos.domain.enums.IncidentNature;
import com.groupnine.bumberoos.domain.enums.IncidentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class IncidentServiceTest {

    @Autowired
    private IncidentService service;

    // Helper: adds an EMERGENCY / ONGOING incident
    private IncidentEntity addSample() {
        return service.addIncident(new IncidentEntity(
                IncidentNature.EMERGENCY, "house fire", "brgy. san antonio",
                LocalDateTime.now(), "juan dela cruz"
        ));
    }

    private IncidentEntity edit(Long id, IncidentNature nature, AlarmLevel alarm, String location) {
        return service.editIncident(id, nature, alarm, null, location, null, null, null, null, null).orElseThrow();
    }

    @Test
    void addIncident_assignsIdAndStoresIt() {
        IncidentEntity added = addSample();

        assertNotNull(added.getId());
        assertEquals("House Fire", added.getIncidentType());
        assertEquals(IncidentNature.EMERGENCY, added.getNature());
        assertEquals(IncidentStatus.ONGOING, added.getStatus());
        assertNull(added.getSeverityLevel(), "severity is optional now");
        assertTrue(service.findById(added.getId()).isPresent());
    }

    @Test
    void addIncident_assignsUniqueIds() {
        assertNotEquals(addSample().getId(), addSample().getId());
    }

    @Test
    void findById_returnsEmptyForUnknownId() {
        assertTrue(service.findById(-999L).isEmpty());
    }

    @Test
    void editIncident_updatesProvidedFields() {
        IncidentEntity added = addSample();

        IncidentEntity edited = service.editIncident(
                added.getId(), null, null, "structural fire", null, null, null, null, null, null
        ).orElseThrow();

        assertEquals("Structural Fire", edited.getIncidentType());
    }

    @Test
    void editIncident_keepsFieldsWhenNull() {
        IncidentEntity added = addSample();
        String originalLocation = added.getLocation();
        String originalReporter = added.getReportedBy();
        LocalDateTime originalDate = added.getDateTimeReported();

        IncidentEntity edited = service.editIncident(
                added.getId(), null, null, null, null, null, null, null, null, null
        ).orElseThrow();

        assertEquals(IncidentNature.EMERGENCY, edited.getNature());
        assertEquals(IncidentStatus.ONGOING, edited.getStatus());
        assertEquals(originalLocation, edited.getLocation());
        assertEquals(originalReporter, edited.getReportedBy());
        assertEquals(originalDate, edited.getDateTimeReported());
    }

    @Test
    void editIncident_returnsEmptyForUnknownId() {
        assertTrue(service.editIncident(-999L, null, null, null, null, null, null, null, null, null).isEmpty());
    }

    @Test
    void editIncident_alarmEscalationIsPersistedInHistory() {
        IncidentEntity added = addSample();

        edit(added.getId(), null, AlarmLevel.FIRST_ALARM, null);
        edit(added.getId(), null, AlarmLevel.SECOND_ALARM, null);

        IncidentEntity reloaded = service.findById(added.getId()).orElseThrow();
        assertEquals(AlarmLevel.SECOND_ALARM, reloaded.getAlarmLevel());
        assertEquals(2, reloaded.getAlarmEvents().size());
        assertEquals(AlarmLevel.FIRST_ALARM, reloaded.getAlarmEvents().get(0).getAlarmLevel());
    }

    @Test
    void getAllIncidents_containsAdded() {
        IncidentEntity added = addSample();

        assertTrue(service.getAllIncidents().stream()
                .anyMatch(i -> i.getId().equals(added.getId())));
    }

    @Test
    void getIncidents_filtersByStatusAndNature() {
        IncidentEntity emergency = addSample();
        IncidentEntity standby = service.addIncident(new IncidentEntity(
                IncidentNature.NON_EMERGENCY, "standby", "plaza", LocalDateTime.now(), "chief"));

        var ongoingNonEmergency = service.getIncidents(IncidentStatus.ONGOING, IncidentNature.NON_EMERGENCY);

        assertTrue(ongoingNonEmergency.stream().anyMatch(i -> i.getId().equals(standby.getId())));
        assertTrue(ongoingNonEmergency.stream().noneMatch(i -> i.getId().equals(emergency.getId())));
        assertTrue(service.getIncidents(IncidentStatus.RESOLVED, null).stream()
                .noneMatch(i -> i.getId().equals(emergency.getId())));
        assertTrue(service.getIncidents(null, IncidentNature.EMERGENCY).stream()
                .anyMatch(i -> i.getId().equals(emergency.getId())));
    }

    // ===== arrival, commander and orders ===== //

    private IncidentEntity editScene(Long id, LocalDateTime arrivedAt, String commander) {
        return service.editIncident(id, null, null, null, null, null, null, null, null, null, arrivedAt, commander)
                .orElseThrow();
    }

    @Test
    void newIncident_hasNoArrivalCommanderOrOrders() {
        IncidentEntity added = addSample();

        assertNull(added.getArrivedAt());
        assertNull(added.getCommanderName());
        assertTrue(added.getCommanderOrders().isEmpty());
    }

    @Test
    void editIncident_recordsArrivalAndCommander() {
        IncidentEntity added = addSample();
        LocalDateTime arrived = LocalDateTime.of(2026, 10, 15, 3, 20);

        IncidentEntity edited = editScene(added.getId(), arrived, "fire insp. ana reyes");

        assertEquals(arrived, edited.getArrivedAt());
        assertEquals("Fire Insp. Ana Reyes", edited.getCommanderName());
    }

    @Test
    void editIncident_nullLeavesSceneFieldsAndBlankClearsCommander() {
        IncidentEntity added = addSample();
        LocalDateTime arrived = LocalDateTime.of(2026, 10, 15, 3, 20);
        editScene(added.getId(), arrived, "ana reyes");

        IncidentEntity unchanged = editScene(added.getId(), null, null);
        assertEquals(arrived, unchanged.getArrivedAt());
        assertEquals("Ana Reyes", unchanged.getCommanderName());

        assertNull(editScene(added.getId(), null, "  ").getCommanderName());
    }

    @Test
    void addCommanderOrder_appendsInOrderWithTimestamp() {
        IncidentEntity added = addSample();

        service.addCommanderOrder(added.getId(), "  Lay a supply line  ").orElseThrow();
        IncidentEntity after = service.addCommanderOrder(added.getId(), "Search the second floor").orElseThrow();

        assertEquals(2, after.getCommanderOrders().size());
        assertEquals("Lay a supply line", after.getCommanderOrders().get(0).getInstruction());
        assertEquals("Search the second floor", after.getCommanderOrders().get(1).getInstruction());
        assertNotNull(after.getCommanderOrders().get(0).getIssuedAt());
    }

    @Test
    void addCommanderOrder_blankIsRejected_unknownIncidentIsEmpty() {
        IncidentEntity added = addSample();

        assertThrows(BadRequestException.class, () -> service.addCommanderOrder(added.getId(), "  "));
        assertTrue(service.addCommanderOrder(-999L, "x").isEmpty());
    }

    @Test
    void addCommanderOrder_resolvedIncidentIsAConflict() {
        IncidentEntity added = addSample();
        added.setStatus(IncidentStatus.RESOLVED);

        assertThrows(ConflictException.class, () -> service.addCommanderOrder(added.getId(), "x"));
    }

    // ===== map position ===== //

    private IncidentEntity move(Long id, Double lat, Double lng) {
        return service.editIncident(id, null, null, null, null, null, null, null, null, null, null, null, lat, lng)
                .orElseThrow();
    }

    @Test
    void editIncident_setsAndMovesTheMapPosition() {
        IncidentEntity added = addSample();
        assertNull(added.getLatitude());

        IncidentEntity placed = move(added.getId(), 14.5995, 120.9842);
        assertEquals(14.5995, placed.getLatitude());
        assertEquals(120.9842, placed.getLongitude());

        IncidentEntity moved = move(added.getId(), 14.6, 121.0);
        assertEquals(14.6, moved.getLatitude());
        assertEquals(121.0, moved.getLongitude());
    }

    @Test
    void editIncident_withNoCoordinatesLeavesThePositionAlone() {
        IncidentEntity added = addSample();
        move(added.getId(), 14.5995, 120.9842);

        IncidentEntity unchanged = move(added.getId(), null, null);

        assertEquals(14.5995, unchanged.getLatitude());
        assertEquals(120.9842, unchanged.getLongitude());
    }

    @Test
    void editIncident_requiresLatitudeAndLongitudeTogether() {
        IncidentEntity added = addSample();

        assertThrows(BadRequestException.class, () -> move(added.getId(), 14.5, null));
        assertThrows(BadRequestException.class, () -> move(added.getId(), null, 120.9));
    }
}
