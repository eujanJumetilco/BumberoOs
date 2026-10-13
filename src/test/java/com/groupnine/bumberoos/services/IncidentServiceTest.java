package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.IncidentEntity;
import com.groupnine.bumberoos.domain.enums.IncidentSeverityLevel;
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

    // Helper: adds a LOW / ONGOING incident
    private IncidentEntity addSample() {
        return service.addIncident(new IncidentEntity(
                IncidentSeverityLevel.LOW, IncidentStatus.ONGOING,
                "house fire", "brgy. san antonio",
                LocalDateTime.now(), "juan dela cruz"
        ));
    }

    @Test
    void addIncident_assignsIdAndStoresIt() {
        IncidentEntity added = addSample();

        assertNotNull(added.getId());
        assertEquals("House Fire", added.getIncidentType());
        assertEquals(IncidentSeverityLevel.LOW, added.getSeverityLevel());
        assertEquals(IncidentStatus.ONGOING, added.getStatus());
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
                added.getId(), IncidentSeverityLevel.CRITICAL, IncidentStatus.PENDING,
                "structural fire", null, null, null
        ).orElseThrow();

        assertEquals(IncidentSeverityLevel.CRITICAL, edited.getSeverityLevel());
        assertEquals(IncidentStatus.PENDING, edited.getStatus());
        assertEquals("Structural Fire", edited.getIncidentType());
    }

    @Test
    void editIncident_keepsFieldsWhenNull() {
        IncidentEntity added = addSample();
        String originalLocation = added.getLocation();
        String originalReporter = added.getReportedBy();
        LocalDateTime originalDate = added.getDateTimeReported();

        IncidentEntity edited = service.editIncident(
                added.getId(), null, null, null, null, null, null
        ).orElseThrow();

        assertEquals(IncidentSeverityLevel.LOW, edited.getSeverityLevel());
        assertEquals(IncidentStatus.ONGOING, edited.getStatus());
        assertEquals(originalLocation, edited.getLocation());
        assertEquals(originalReporter, edited.getReportedBy());
        assertEquals(originalDate, edited.getDateTimeReported());
    }

    @Test
    void editIncident_returnsEmptyForUnknownId() {
        assertTrue(service.editIncident(-999L, null, null, null, null, null, null).isEmpty());
    }

    @Test
    void getAllIncidents_containsAdded() {
        IncidentEntity added = addSample();

        assertTrue(service.getAllIncidents().stream()
                .anyMatch(i -> i.getId().equals(added.getId())));
    }

    @Test
    void removeIncident_removesExistingAndReturnsFalseAfterwards() {
        IncidentEntity added = addSample();

        assertTrue(service.removeIncident(added.getId()));
        assertTrue(service.findById(added.getId()).isEmpty());
        assertFalse(service.removeIncident(added.getId()));
    }

    @Test
    void removeIncident_returnsFalseForUnknownId() {
        assertFalse(service.removeIncident(-999L));
    }
}
