package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.domain.entities.UserAccountEntity;
import com.groupnine.bumberoos.domain.enums.UserRole;
import com.groupnine.bumberoos.repositories.UserAccountRepository;
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

@ExtendWith(MockitoExtension.class)
class UserAccountServiceMockTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @InjectMocks
    private UserAccountService service;

    private UserAccountEntity mockAccount(long id) {
        UserAccountEntity a = new UserAccountEntity("A@B.com", "hash", UserRole.ADMIN, "juan");
        a.setId(id);
        return a;
    }

    @Test
    void addAccount_clearsIdAndSaves() {
        UserAccountEntity input = mockAccount(9L);
        when(userAccountRepository.save(any(UserAccountEntity.class))).thenAnswer(i -> i.getArgument(0));

        UserAccountEntity result = service.addAccount(input);

        assertNull(result.getId());
        assertEquals("a@b.com", result.getEmail());
        verify(userAccountRepository).save(input);
    }

    @Test
    void removeAccount_deletesWhenExists_andFalseWhenMissing() {
        when(userAccountRepository.existsById(1L)).thenReturn(true);
        when(userAccountRepository.existsById(2L)).thenReturn(false);

        assertTrue(service.removeAccount(1L));
        assertFalse(service.removeAccount(2L));
        verify(userAccountRepository).deleteById(1L);
        verify(userAccountRepository, never()).deleteById(2L);
    }

    @Test
    void setEnabled_updatesFlag() {
        UserAccountEntity a = mockAccount(1L);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(a));
        when(userAccountRepository.save(a)).thenReturn(a);

        assertFalse(service.setEnabled(1L, false).orElseThrow().isEnabled());
    }

    @Test
    void setEnabled_returnsEmptyWhenMissing() {
        when(userAccountRepository.findById(404L)).thenReturn(Optional.empty());

        assertTrue(service.setEnabled(404L, false).isEmpty());
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void linkStaff_linksAndUnlinks() {
        UserAccountEntity a = mockAccount(1L);
        StaffEntity staff = new StaffEntity("juan", "driver", "09171234567", "pasay", true,
                new ArrayList<>(), new ArrayList<>());
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(a));
        when(userAccountRepository.save(a)).thenReturn(a);

        assertSame(staff, service.linkStaff(1L, staff).orElseThrow().getStaff());
        assertNull(service.linkStaff(1L, null).orElseThrow().getStaff());
    }

    @Test
    void queries_delegateToRepository() {
        UserAccountEntity a = mockAccount(1L);
        when(userAccountRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(a));
        when(userAccountRepository.existsByEmailIgnoreCase("a@b.com")).thenReturn(true);
        when(userAccountRepository.existsByRoleAndEnabledTrue(UserRole.ADMIN)).thenReturn(true);
        when(userAccountRepository.findAll()).thenReturn(List.of(a));

        assertSame(a, service.findByEmail("a@b.com").orElseThrow());
        assertTrue(service.emailExists("a@b.com"));
        assertTrue(service.hasEnabledAdmin());
        assertEquals(1, service.getAllAccounts().size());
    }
}
