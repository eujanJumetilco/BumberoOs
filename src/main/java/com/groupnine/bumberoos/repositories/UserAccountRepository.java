package com.groupnine.bumberoos.repositories;

import com.groupnine.bumberoos.domain.entities.UserAccountEntity;
import com.groupnine.bumberoos.domain.enums.UserRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, Long> {

    Optional<UserAccountEntity> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRoleAndEnabledTrue(UserRole role);

    boolean existsByStaffId(Long staffId);

    /** Locks every enabled admin row, so concurrent changes cannot each leave the system without one. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from UserAccountEntity a where a.role = com.groupnine.bumberoos.domain.enums.UserRole.ADMIN and a.enabled = true")
    List<UserAccountEntity> lockEnabledAdmins();
}
