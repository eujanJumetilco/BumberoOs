package com.groupnine.bumberoos.services;

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
}
