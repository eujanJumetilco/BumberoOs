package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.domain.entities.UserAccountEntity;
import com.groupnine.bumberoos.domain.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Integration tests against in-memory H2: the account/staff split as the database sees it. */
@SpringBootTest
@Transactional
class UserAccountServiceTest {

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private StaffService staff;

    @Test
    void account_persistsWithoutStaffRecord() {
        UserAccountEntity saved = accounts.addAccount(
                new UserAccountEntity("Admin@Test.com", "hash", UserRole.ADMIN, "maria"));

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNull(saved.getStaff());
        assertTrue(accounts.findByEmail("ADMIN@test.com").isPresent());
        assertTrue(accounts.hasEnabledAdmin());
    }

    @Test
    void account_canBeLinkedToStaff_andStaffSurvivesAccountRemoval() {
        StaffEntity s = staff.addStaff(new StaffEntity("juan", "driver", "09171234567", "pasay",
                true, new ArrayList<>(), new ArrayList<>()));
        UserAccountEntity a = accounts.addAccount(
                new UserAccountEntity("juan@test.com", "hash", UserRole.DISPATCHER, "juan"));

        accounts.linkStaff(a.getId(), s);
        assertEquals(s.getId(), accounts.findById(a.getId()).orElseThrow().getStaff().getId());

        assertTrue(accounts.removeAccount(a.getId()));
        assertTrue(staff.findById(s.getId()).isPresent());
    }

    @Test
    void disabledAdmin_doesNotCountAsRegistered() {
        UserAccountEntity a = accounts.addAccount(
                new UserAccountEntity("off@test.com", "hash", UserRole.ADMIN, "off"));
        accounts.setEnabled(a.getId(), false);

        assertFalse(accounts.hasEnabledAdmin());
    }

    @Test
    void duplicateEmail_isRejectedByDatabase() {
        accounts.addAccount(new UserAccountEntity("dup@test.com", "h", UserRole.ADMIN, "a"));

        assertThrows(DataIntegrityViolationException.class, () -> {
            accounts.addAccount(new UserAccountEntity("DUP@test.com", "h", UserRole.ADMIN, "b"));
            accounts.getAllAccounts(); // forces flush
        });
    }
}
