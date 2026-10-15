package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.exceptions.BadRequestException;
import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.domain.enums.StaffAvailability;
import com.groupnine.bumberoos.domain.enums.UserRole;
import com.groupnine.bumberoos.domain.entities.UserAccountEntity;
import com.groupnine.bumberoos.domain.exceptions.ConflictException;
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

    @Autowired
    private UserAccountService accountService;

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
                null, null, StaffAvailability.AVAILABLE
        ).orElseThrow();

        assertEquals("New Name", edited.getName());
        assertEquals("Driver", edited.getRole());
        assertFalse(edited.isEmployed());
        assertEquals(StaffAvailability.AVAILABLE, edited.getStaffAvailability());
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

    @Test
    void editStaff_manualDispatchedIsRejected() {
        StaffEntity added = service.addStaff(sample());

        assertThrows(ConflictException.class, () -> service.editStaff(
                added.getId(), null, null, null, null, null, null, null, StaffAvailability.DISPATCHED));
    }

    @Test
    void getStaffList_filtersByAvailability() {
        StaffEntity available = service.addStaff(sample());
        service.editStaff(available.getId(), null, null, null, null, null, null, null, StaffAvailability.AVAILABLE);
        StaffEntity unavailable = service.addStaff(sample());

        var result = service.getStaffList(StaffAvailability.AVAILABLE);

        assertTrue(result.stream().anyMatch(s -> s.getId().equals(available.getId())));
        assertTrue(result.stream().noneMatch(s -> s.getId().equals(unavailable.getId())));
        assertTrue(service.getStaffList((StaffAvailability) null).size() >= 2);
    }

    @Test
    void removeStaff_blockedWhileLinkedToAnAccount() {
        StaffEntity added = service.addStaff(sample());
        UserAccountEntity account = accountService.addAccount(
                new UserAccountEntity("linked@bumberoos.com", "hash", UserRole.DISPATCHER, "linked"));
        accountService.linkStaff(account.getId(), added);

        assertThrows(ConflictException.class, () -> service.removeStaff(added.getId()));

        accountService.linkStaff(account.getId(), null);
        assertTrue(service.removeStaff(added.getId()));
    }

    // ===== staff ID (badge number) ===== //

    private StaffEntity withBadge(String badge) {
        StaffEntity s = sample();
        s.setBadgeNumber(badge);
        return s;
    }

    @Test
    void addStaff_storesBadgeNumber() {
        assertEquals("2516528", service.addStaff(withBadge(" 2516528 ")).getBadgeNumber());
    }

    @Test
    void addStaff_withoutBadgeIsAllowed_andBlankBecomesNull() {
        assertNull(service.addStaff(sample()).getBadgeNumber());
        assertNull(service.addStaff(withBadge("  ")).getBadgeNumber());
    }

    @Test
    void addStaff_rejectsMalformedBadge() {
        assertThrows(BadRequestException.class, () -> service.addStaff(withBadge("123")));
        assertThrows(BadRequestException.class, () -> service.addStaff(withBadge("25165289")));
        assertThrows(BadRequestException.class, () -> service.addStaff(withBadge("25165a8")));
    }

    @Test
    void addStaff_rejectsDuplicateBadge() {
        service.addStaff(withBadge("2516528"));

        ConflictException e = assertThrows(ConflictException.class, () -> service.addStaff(withBadge("2516528")));
        assertTrue(e.getMessage().contains("2516528"));
    }

    @Test
    void editStaff_setsChangesAndClearsBadge() {
        StaffEntity added = service.addStaff(sample());

        assertEquals("1111111", service.editStaff(added.getId(), null, null, null, null, null, null, null, null,
                "1111111").orElseThrow().getBadgeNumber());
        assertEquals("2222222", service.editStaff(added.getId(), null, null, null, null, null, null, null, null,
                "2222222").orElseThrow().getBadgeNumber());
        assertEquals("2222222", service.editStaff(added.getId(), null, null, null, null, null, null, null, null,
                null).orElseThrow().getBadgeNumber(), "null leaves it unchanged");
        assertNull(service.editStaff(added.getId(), null, null, null, null, null, null, null, null,
                "").orElseThrow().getBadgeNumber(), "blank clears it");
    }

    @Test
    void editStaff_keepingOwnBadgeIsNotAConflict() {
        StaffEntity added = service.addStaff(withBadge("2516528"));

        assertDoesNotThrow(() -> service.editStaff(added.getId(), null, null, null, null, null, null, null, null,
                "2516528"));
    }

    @Test
    void editStaff_rejectsBadgeUsedBySomeoneElse() {
        service.addStaff(withBadge("2516528"));
        StaffEntity other = service.addStaff(sample());

        assertThrows(ConflictException.class, () -> service.editStaff(other.getId(), null, null, null, null, null,
                null, null, null, "2516528"));
        assertThrows(BadRequestException.class, () -> service.editStaff(other.getId(), null, null, null, null, null,
                null, null, null, "12"));
    }
}
