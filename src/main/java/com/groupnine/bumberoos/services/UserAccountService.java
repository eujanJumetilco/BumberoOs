package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.domain.entities.UserAccountEntity;
import com.groupnine.bumberoos.domain.enums.UserRole;
import com.groupnine.bumberoos.repositories.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;

    public UserAccountService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    /** The password hash must already be encoded. */
    public UserAccountEntity addAccount(UserAccountEntity account) {
        account.setId(null);
        return userAccountRepository.save(account);
    }

    public boolean removeAccount(Long id) {
        if (!userAccountRepository.existsById(id)) return false;
        userAccountRepository.deleteById(id);
        return true;
    }

    public Optional<UserAccountEntity> setEnabled(Long id, boolean enabled) {
        return userAccountRepository.findById(id).map(account -> {
            account.setEnabled(enabled);
            return userAccountRepository.save(account);
        });
    }

    /** Links (or, with null, unlinks) the account to a staff member. */
    public Optional<UserAccountEntity> linkStaff(Long id, StaffEntity staff) {
        return userAccountRepository.findById(id).map(account -> {
            account.setStaff(staff);
            return userAccountRepository.save(account);
        });
    }

    @Transactional(readOnly = true)
    public Optional<UserAccountEntity> findById(Long id) {
        return userAccountRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<UserAccountEntity> findByEmail(String email) {
        return userAccountRepository.findByEmailIgnoreCase(email);
    }

    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        return userAccountRepository.existsByEmailIgnoreCase(email);
    }

    @Transactional(readOnly = true)
    public boolean hasEnabledAdmin() {
        return userAccountRepository.existsByRoleAndEnabledTrue(UserRole.ADMIN);
    }

    @Transactional(readOnly = true)
    public List<UserAccountEntity> getAllAccounts() {
        return userAccountRepository.findAll();
    }
}
