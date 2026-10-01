package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.IncidentEntity;
import com.groupnine.bumberoos.domain.enums.IncidentSeverityLevel;
import com.groupnine.bumberoos.domain.enums.IncidentStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class IncidentServiceTest {

    private IncidentService service;
    private final ArrayList<Integer> createdIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new IncidentService();
        createdIds.clear();
    }

    @AfterEach
    void tearDown() {
        for (int id : createdIds) {
            service.removeIncident(id);
        }
    }

    // Helper: adds a LOW / ONGOING incident and tracks it for cleanup
    private IncidentEntity addSample() {
        IncidentEntity added = service.addIncident(new IncidentEntity(
                0, 0, 0, "house fire", "brgy. san antonio",
                LocalDateTime.now(), "juan dela cruz"
        ));
        createdIds.add(added.getId());
        return added;
    }

    @Test
    void addIncident_assignsIdAndStoresIt() {
        int expectedId = service.getNextId();

        IncidentEntity added = addSample();

        assertEquals(expectedId, added.getId());
        assertEquals(expectedId + 1, service.getNextId());
        assertEquals(IncidentSeverityLevel.LOW, added.getSeverityLevel());
        assertEquals(IncidentStatus.ONGOING, added.getStatus());
        assertSame(added, service.findById(added.getId()));
    }

    @Test
    void addIncident_assignsUniqueIds() {
        IncidentEntity first = addSample();
        IncidentEntity second = addSample();

        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void findById_returnsNullForUnknownId() {
        assertNull(service.findById(-999));
    }

    @Test
    void editIncident_updatesProvidedFields() {
        IncidentEntity added = addSample();

        IncidentEntity edited = service.editIncident(
                added.getId(), 3, 1, "structural fire", null, null, null
        );

        assertNotNull(edited);
        assertEquals(IncidentSeverityLevel.CRITICAL, edited.getSeverityLevel());
        assertEquals(IncidentStatus.PENDING, edited.getStatus());
        assertTrue(edited.getIncidentType().equalsIgnoreCase("structural fire"));
    }

    @Test
    void editIncident_keepsFieldsWhenNullOrMinusOne() {
        IncidentEntity added = addSample();
        String originalLocation = added.getLocation();
        String originalReporter = added.getReportedBy();
        LocalDateTime originalDate = added.getDateTimeReported();

        IncidentEntity edited = service.editIncident(
                added.getId(), -1, -1, null, null, null, null
        );

        assertEquals(IncidentSeverityLevel.LOW, edited.getSeverityLevel());
        assertEquals(IncidentStatus.ONGOING, edited.getStatus());
        assertEquals(originalLocation, edited.getLocation());
        assertEquals(originalReporter, edited.getReportedBy());
        assertEquals(originalDate, edited.getDateTimeReported());
    }

    @Test
    void editIncident_returnsNullForUnknownId() {
        assertNull(service.editIncident(-999, -1, -1, null, null, null, null));
    }

    @Test
    void getAllIncidents_containsAddedAndReturnsCopy() {
        IncidentEntity added = addSample();

        ArrayList<IncidentEntity> all = service.getAllIncidents();
        assertTrue(all.contains(added));

        // Modifying the returned list must not affect the service
        all.clear();
        assertNotNull(service.findById(added.getId()));
    }

    @Test
    void removeIncident_removesExistingAndReturnsFalseAfterwards() {
        IncidentEntity added = addSample();

        assertTrue(service.removeIncident(added.getId()));
        assertNull(service.findById(added.getId()));
        assertFalse(service.removeIncident(added.getId()));
    }

    @Test
    void removeIncident_returnsFalseForUnknownId() {
        assertFalse(service.removeIncident(-999));
    }

    @Test
    void saveAll_persistsToFile() {
        IncidentEntity added = addSample();

        // A new service instance loads from the file
        IncidentService reloaded = new IncidentService();
        IncidentEntity persisted = reloaded.findById(added.getId());

        assertNotNull(persisted);
        assertEquals(added.getSeverityLevel(), persisted.getSeverityLevel());
        assertEquals(added.getStatus(), persisted.getStatus());
    }
}