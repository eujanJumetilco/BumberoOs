package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.domain.enums.StaffAvailability;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class StaffServiceTest {

    @Autowired
    private StaffService service;

    private StaffEntity sample() {
        return new StaffEntity(
                "test person", "driver", "09171234567",
                "paranaque city", true,
                new ArrayList<>(List.of("first aid")),
                new ArrayList<>(List.of("professional license"))
        );
    }

    @Test
    void addStaff_assignsIdAndNormalizes() {
        StaffEntity added = service.addStaff(sample());

        assertNotNull(added.getId());
        assertEquals("Test Person", added.getName());
        assertEquals("Driver", added.getRole());
        assertEquals(List.of("First Aid"), added.getTrainings());
        assertNotNull(added.getDateJoined());
        assertEquals(StaffAvailability.UNAVAILABLE, added.getStaffAvailability());
    }

    @Test
    void findById_returnsEmptyForUnknownId() {
        assertTrue(service.findById(-999L).isEmpty());
    }

    @Test
    void editStaff_updatesOnlyProvidedFields() {
        StaffEntity added = service.addStaff(sample());

        StaffEntity edited = service.editStaff(
                added.getId(), "new name", null, null, null, false,
                null, null, StaffAvailability.DISPATCHED
        ).orElseThrow();

        assertEquals("New Name", edited.getName());
        assertEquals("Driver", edited.getRole());
        assertFalse(edited.isEmployed());
        assertEquals(StaffAvailability.DISPATCHED, edited.getStaffAvailability());
    }

    @Test
    void editStaff_returnsEmptyForUnknownId() {
        assertTrue(service.editStaff(-999L, null, null, null, null, null, null, null, null).isEmpty());
    }

    @Test
    void removeStaff_removesExistingAndReturnsFalseAfterwards() {
        StaffEntity added = service.addStaff(sample());

        assertTrue(service.removeStaff(added.getId()));
        assertTrue(service.findById(added.getId()).isEmpty());
        assertFalse(service.removeStaff(added.getId()));
    }

}
