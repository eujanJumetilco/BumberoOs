package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.IncidentEntity;
import com.groupnine.bumberoos.domain.enums.AlarmLevel;
import com.groupnine.bumberoos.domain.enums.IncidentNature;
import com.groupnine.bumberoos.domain.enums.IncidentSeverityLevel;
import com.groupnine.bumberoos.domain.enums.IncidentStatus;
import com.groupnine.bumberoos.domain.exceptions.BadRequestException;
import com.groupnine.bumberoos.domain.exceptions.ConflictException;
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
@SuppressWarnings("deprecation")
class IncidentServiceMockTest {

    private static final LocalDateTime REPORTED = LocalDateTime.of(2026, 1, 15, 8, 30);

    @Mock
    private IncidentRepository incidentRepository;

    @InjectMocks
    private IncidentService service;

    private IncidentEntity mockIncident(long id) {
        IncidentEntity i = new IncidentEntity(
                IncidentNature.EMERGENCY, "house fire", "brgy. san antonio", REPORTED, "juan dela cruz"
        );
        i.setId(id);
        return i;
    }

    private void found(IncidentEntity incident) {
        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));
        lenient().when(incidentRepository.save(incident)).thenReturn(incident);
    }

    private IncidentEntity edit(Long id, IncidentNature nature, AlarmLevel alarm) {
        return service.editIncident(id, nature, alarm, null, null, null, null, null, null, null).orElseThrow();
    }

    @Test
    void addIncident_clearsIdForcesOngoingAndSaves() {
        IncidentEntity input = mockIncident(77L);
        input.setStatus(IncidentStatus.RESOLVED);
        when(incidentRepository.save(any(IncidentEntity.class))).thenAnswer(i -> i.getArgument(0));

        IncidentEntity result = service.addIncident(input);

        assertNull(result.getId());
        assertEquals(IncidentStatus.ONGOING, result.getStatus());
        assertEquals("House Fire", result.getIncidentType());
        verify(incidentRepository).save(input);
    }

    @Test
    void addIncident_recordsFirstAlarmEvent() {
        IncidentEntity input = mockIncident(1L);
        input.setAlarmLevel(AlarmLevel.FIRST_ALARM);
        when(incidentRepository.save(any(IncidentEntity.class))).thenAnswer(i -> i.getArgument(0));

        IncidentEntity result = service.addIncident(input);

        assertEquals(1, result.getAlarmEvents().size());
        assertEquals(AlarmLevel.FIRST_ALARM, result.getAlarmEvents().get(0).getAlarmLevel());
    }

    @Test
    void addIncident_rejectsAlarmOnNonEmergency() {
        IncidentEntity input = new IncidentEntity(IncidentNature.NON_EMERGENCY, "standby", "plaza", REPORTED, "chief");
        input.setAlarmLevel(AlarmLevel.FIRST_ALARM);

        assertThrows(BadRequestException.class, () -> service.addIncident(input));
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void editIncident_updatesProvidedFields() {
        IncidentEntity existing = mockIncident(1L);
        LocalDateTime newDate = REPORTED.plusHours(2);
        LocalDateTime underControl = REPORTED.plusHours(1);
        found(existing);

        IncidentEntity edited = service.editIncident(
                1L, null, null, "structural fire", "makati city", newDate, "maria santos",
                underControl, REPORTED.plusHours(3), IncidentSeverityLevel.CRITICAL
        ).orElseThrow();

        assertEquals("Structural Fire", edited.getIncidentType());
        assertEquals("Makati city", edited.getLocation());
        assertEquals(newDate, edited.getDateTimeReported());
        assertEquals("Maria Santos", edited.getReportedBy());
        assertEquals(underControl, edited.getUnderControlAt());
        assertEquals(REPORTED.plusHours(3), edited.getFireOutAt());
        assertEquals(IncidentSeverityLevel.CRITICAL, edited.getSeverityLevel());
    }

    @Test
    void editIncident_allNullsChangesNothing() {
        IncidentEntity existing = mockIncident(1L);
        found(existing);

        IncidentEntity edited = edit(1L, null, null);

        assertEquals(IncidentNature.EMERGENCY, edited.getNature());
        assertEquals(IncidentStatus.ONGOING, edited.getStatus());
        assertEquals("Brgy. san antonio", edited.getLocation());
        assertEquals(REPORTED, edited.getDateTimeReported());
        assertTrue(edited.getAlarmEvents().isEmpty());
    }

    @Test
    void editIncident_returnsEmptyAndDoesNotSaveWhenMissing() {
        when(incidentRepository.findById(404L)).thenReturn(Optional.empty());

        assertTrue(service.editIncident(404L, null, null, null, null, null, null, null, null, null).isEmpty());
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void editIncident_alarmChangeAppendsEventAndSameValueDoesNot() {
        IncidentEntity existing = mockIncident(1L);
        found(existing);

        edit(1L, null, AlarmLevel.SECOND_ALARM);
        edit(1L, null, AlarmLevel.SECOND_ALARM);
        edit(1L, null, AlarmLevel.FIRST_ALARM); // downgrade allowed

        assertEquals(AlarmLevel.FIRST_ALARM, existing.getAlarmLevel());
        assertEquals(List.of(AlarmLevel.SECOND_ALARM, AlarmLevel.FIRST_ALARM),
                existing.getAlarmEvents().stream().map(e -> e.getAlarmLevel()).toList());
    }

    @Test
    void editIncident_alarmOnNonEmergencyIsRejected() {
        IncidentEntity existing = new IncidentEntity(IncidentNature.NON_EMERGENCY, "standby", "plaza", REPORTED, "chief");
        existing.setId(1L);
        found(existing);

        assertThrows(BadRequestException.class, () -> edit(1L, null, AlarmLevel.FIRST_ALARM));
        assertNull(existing.getAlarmLevel());
    }

    @Test
    void editIncident_alarmWithSwitchToNonEmergencyInSameRequestIsRejected() {
        IncidentEntity existing = mockIncident(1L);
        found(existing);

        assertThrows(BadRequestException.class, () -> edit(1L, IncidentNature.NON_EMERGENCY, AlarmLevel.FIRST_ALARM));
        assertEquals(IncidentNature.EMERGENCY, existing.getNature());
    }

    @Test
    void editIncident_switchingToNonEmergencyClearsAlarm() {
        IncidentEntity existing = mockIncident(1L);
        existing.setAlarmLevel(AlarmLevel.SECOND_ALARM);
        found(existing);

        IncidentEntity edited = edit(1L, IncidentNature.NON_EMERGENCY, null);

        assertEquals(IncidentNature.NON_EMERGENCY, edited.getNature());
        assertNull(edited.getAlarmLevel());
        assertTrue(edited.getAlarmEvents().isEmpty(), "clearing the alarm is not an escalation");
    }

    @Test
    void editIncident_resolvedRejectsNatureAndAlarmChangesButAllowsText() {
        IncidentEntity existing = mockIncident(1L);
        existing.setStatus(IncidentStatus.RESOLVED);
        found(existing);

        assertThrows(ConflictException.class, () -> edit(1L, IncidentNature.NON_EMERGENCY, null));
        assertThrows(ConflictException.class, () -> edit(1L, null, AlarmLevel.THIRD_ALARM));

        IncidentEntity edited = service.editIncident(
                1L, null, null, null, "new location", null, null, null, null, null).orElseThrow();
        assertEquals("New location", edited.getLocation());
    }

    @Test
    void findById_andGetAll_delegateToRepository() {
        IncidentEntity a = mockIncident(1L);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(a));
        when(incidentRepository.findAll()).thenReturn(List.of(a, mockIncident(2L)));

        assertSame(a, service.findById(1L).orElseThrow());
        assertEquals(2, service.getAllIncidents().size());
    }

    @Test
    void getIncidents_choosesRepositoryQueryByFilters() {
        service.getIncidents(null, null);
        service.getIncidents(IncidentStatus.ONGOING, null);
        service.getIncidents(null, IncidentNature.EMERGENCY);
        service.getIncidents(IncidentStatus.RESOLVED, IncidentNature.NON_EMERGENCY);

        verify(incidentRepository).findAll();
        verify(incidentRepository).findByStatus(IncidentStatus.ONGOING);
        verify(incidentRepository).findByNature(IncidentNature.EMERGENCY);
        verify(incidentRepository).findByStatusAndNature(IncidentStatus.RESOLVED, IncidentNature.NON_EMERGENCY);
    }
}
