package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.domain.entities.UserAccountEntity;
import com.groupnine.bumberoos.domain.enums.UserRole;
import com.groupnine.bumberoos.domain.exceptions.ConflictException;
import com.groupnine.bumberoos.repositories.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Admin actions on accounts, with the safety rules that keep the system usable: an admin cannot lock
 * themselves out, and there is always at least one enabled administrator.
 *
 * <p>{@code actingId} is the signed-in account; null when the caller is not a session account.
 */
@Service
@Transactional
public class AccountAdministrationService {

    private final UserAccountRepository accounts;

    public AccountAdministrationService(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    public Optional<UserAccountEntity> setEnabled(Long actingId, Long id, boolean enabled) {
        return accounts.findById(id).map(account -> {
            if (!enabled && account.isEnabled()) {
                requireNotSelf(actingId, id, "disable your own account");
                requireAnotherEnabledAdmin(account, "disable the last enabled administrator");
            }
            account.setEnabled(enabled);
            return accounts.save(account);
        });
    }

    public Optional<UserAccountEntity> setRole(Long actingId, Long id, UserRole role) {
        return accounts.findById(id).map(account -> {
            if (account.getRole() != role) {
                requireNotSelf(actingId, id, "change your own role");
                if (role != UserRole.ADMIN) requireAnotherEnabledAdmin(account, "demote the last enabled administrator");
            }
            account.setRole(role);
            return accounts.save(account);
        });
    }

    /**
     * Links the account to a staff member, or unlinks it when {@code staff} is null.
     *
     * @throws ConflictException if another account is already linked to that staff member
     */
    public Optional<UserAccountEntity> linkStaff(Long id, StaffEntity staff) {
        return accounts.findById(id).map(account -> {
            if (staff != null) {
                accounts.findByStaffId(staff.getId()).ifPresent(other -> {
                    if (!other.getId().equals(id)) {
                        throw new ConflictException(staff.getName() + " is already linked to " + other.getEmail());
                    }
                });
            }
            account.setStaff(staff);
            return accounts.save(account);
        });
    }

    /** @return false if the account does not exist. */
    public boolean remove(Long actingId, Long id) {
        Optional<UserAccountEntity> found = accounts.findById(id);
        if (found.isEmpty()) return false;
        requireNotSelf(actingId, id, "delete your own account");
        requireAnotherEnabledAdmin(found.get(), "delete the last enabled administrator");
        accounts.delete(found.get());
        return true;
    }

    private void requireNotSelf(Long actingId, Long id, String action) {
        if (id.equals(actingId)) throw new ConflictException("you cannot " + action);
    }

    /** No-op unless {@code account} is an enabled admin; then another enabled admin must exist. */
    private void requireAnotherEnabledAdmin(UserAccountEntity account, String action) {
        if (account.getRole() != UserRole.ADMIN || !account.isEnabled()) return;
        boolean another = accounts.lockEnabledAdmins().stream().anyMatch(a -> !a.getId().equals(account.getId()));
        if (!another) throw new ConflictException("you cannot " + action);
    }
}
