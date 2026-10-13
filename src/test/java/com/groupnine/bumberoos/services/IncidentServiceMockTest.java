package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.IncidentEntity;
import com.groupnine.bumberoos.domain.enums.IncidentSeverityLevel;
import com.groupnine.bumberoos.domain.enums.IncidentStatus;
import com.groupnine.bumberoos.repositories.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests for IncidentService with a mocked IncidentRepository (no database). */
@ExtendWith(MockitoExtension.class)
class IncidentServiceMockTest {

    private static final LocalDateTime REPORTED = LocalDateTime.of(2026, 1, 15, 8, 30);

    @Mock
    private IncidentRepository incidentRepository;

    @InjectMocks
    private IncidentService service;

    private IncidentEntity mockIncident(long id) {
        IncidentEntity i = new IncidentEntity(
                IncidentSeverityLevel.LOW, IncidentStatus.ONGOING,
                "house fire", "brgy. san antonio", REPORTED, "juan dela cruz"
        );
        i.setId(id);
        return i;
    }

    @Test
    void addIncident_clearsIdAndSavesViaRepository() {
        IncidentEntity input = mockIncident(77L);
        when(incidentRepository.save(any(IncidentEntity.class))).thenAnswer(i -> i.getArgument(0));

        IncidentEntity result = service.addIncident(input);

        assertNull(result.getId());
        assertEquals("House Fire", result.getIncidentType());
        verify(incidentRepository).save(input);
    }

    @Test
    void removeIncident_deletesWhenExists() {
        when(incidentRepository.existsById(1L)).thenReturn(true);

        assertTrue(service.removeIncident(1L));
        verify(incidentRepository).deleteById(1L);
    }

    @Test
    void removeIncident_returnsFalseWhenMissing() {
        when(incidentRepository.existsById(9L)).thenReturn(false);

        assertFalse(service.removeIncident(9L));
        verify(incidentRepository, never()).deleteById(any());
    }

    @Test
    void editIncident_updatesProvidedFields() {
        IncidentEntity existing = mockIncident(1L);
        LocalDateTime newDate = REPORTED.plusHours(2);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(incidentRepository.save(existing)).thenReturn(existing);

        IncidentEntity edited = service.editIncident(
                1L, IncidentSeverityLevel.CRITICAL, IncidentStatus.PENDING,
                "structural fire", "makati city", newDate, "maria santos"
        ).orElseThrow();

        assertEquals(IncidentSeverityLevel.CRITICAL, edited.getSeverityLevel());
        assertEquals(IncidentStatus.PENDING, edited.getStatus());
        assertEquals("Structural Fire", edited.getIncidentType());
        assertEquals("Makati city", edited.getLocation());
        assertEquals(newDate, edited.getDateTimeReported());
        assertEquals("Maria Santos", edited.getReportedBy());
    }

    @Test
    void editIncident_allNullsChangesNothing() {
        IncidentEntity existing = mockIncident(1L);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(incidentRepository.save(existing)).thenReturn(existing);

        IncidentEntity edited = service.editIncident(1L, null, null, null, null, null, null).orElseThrow();

        assertEquals(IncidentSeverityLevel.LOW, edited.getSeverityLevel());
        assertEquals(IncidentStatus.ONGOING, edited.getStatus());
        assertEquals("Brgy. san antonio", edited.getLocation());
        assertEquals(REPORTED, edited.getDateTimeReported());
    }

    @Test
    void editIncident_returnsEmptyAndDoesNotSaveWhenMissing() {
        when(incidentRepository.findById(404L)).thenReturn(Optional.empty());

        assertTrue(service.editIncident(404L, null, null, null, null, null, null).isEmpty());
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void findById_andGetAll_delegateToRepository() {
        IncidentEntity a = mockIncident(1L);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(a));
        when(incidentRepository.findAll()).thenReturn(List.of(a, mockIncident(2L)));

        assertSame(a, service.findById(1L).orElseThrow());
        assertEquals(2, service.getAllIncidents().size());
    }
}
