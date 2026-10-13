package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.domain.enums.StaffAvailability;
import com.groupnine.bumberoos.repositories.StaffRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests for StaffService with a mocked StaffRepository (no database). */
@ExtendWith(MockitoExtension.class)
class StaffServiceMockTest {

    @Mock
    private StaffRepository staffRepository;

    @InjectMocks
    private StaffService service;

    private StaffEntity mockStaff(long id) {
        StaffEntity s = new StaffEntity(
                "  juan   dela cruz ", "driver", "0917-123-4567",
                "paranaque city", true,
                new ArrayList<>(List.of("first aid")), new ArrayList<>()
        );
        s.setId(id);
        return s;
    }

    @Test
    void addStaff_clearsIdAndSavesViaRepository() {
        StaffEntity input = mockStaff(99L);
        when(staffRepository.save(any(StaffEntity.class))).thenAnswer(i -> i.getArgument(0));

        StaffEntity result = service.addStaff(input);

        assertNull(result.getId(), "caller-supplied id must be discarded so the DB assigns one");
        assertEquals("Juan Dela Cruz", result.getName());
        assertEquals("09171234567", result.getContactNumber());
        verify(staffRepository).save(input);
    }

    @Test
    void removeStaff_deletesWhenExists() {
        when(staffRepository.existsById(1L)).thenReturn(true);

        assertTrue(service.removeStaff(1L));
        verify(staffRepository).deleteById(1L);
    }

    @Test
    void removeStaff_returnsFalseAndDeletesNothingWhenMissing() {
        when(staffRepository.existsById(5L)).thenReturn(false);

        assertFalse(service.removeStaff(5L));
        verify(staffRepository, never()).deleteById(any());
    }

    @Test
    void editStaff_updatesProvidedFieldsOnly() {
        StaffEntity existing = mockStaff(1L);
        when(staffRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(staffRepository.save(existing)).thenReturn(existing);

        StaffEntity edited = service.editStaff(
                1L, "maria santos", null, null, "makati city", false,
                List.of("rescue", "  "), null, StaffAvailability.AVAILABLE
        ).orElseThrow();

        assertEquals("Maria Santos", edited.getName());
        assertEquals("Driver", edited.getRole());              // untouched
        assertEquals("09171234567", edited.getContactNumber()); // untouched
        assertEquals("Makati city", edited.getAddress());
        assertFalse(edited.isEmployed());
        assertEquals(List.of("Rescue"), edited.getTrainings()); // blank entry dropped
        assertEquals(StaffAvailability.AVAILABLE, edited.getStaffAvailability());
    }

    @Test
    void editStaff_returnsEmptyAndDoesNotSaveWhenMissing() {
        when(staffRepository.findById(404L)).thenReturn(Optional.empty());

        assertTrue(service.editStaff(404L, "x", null, null, null, null, null, null, null).isEmpty());
        verify(staffRepository, never()).save(any());
    }

    @Test
    void findById_delegatesToRepository() {
        StaffEntity s = mockStaff(2L);
        when(staffRepository.findById(2L)).thenReturn(Optional.of(s));

        assertSame(s, service.findById(2L).orElseThrow());
        assertTrue(service.findById(3L).isEmpty());
    }


    @Test
    void getStaffList_returnsAllFromRepository() {
        when(staffRepository.findAll()).thenReturn(List.of(mockStaff(1L), mockStaff(2L)));

        assertEquals(2, service.getStaffList().size());
    }
}
