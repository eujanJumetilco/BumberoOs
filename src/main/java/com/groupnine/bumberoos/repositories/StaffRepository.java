package com.groupnine.bumberoos.repositories;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.domain.enums.StaffAvailability;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<StaffEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select x from StaffEntity x where x.id = :id")
    Optional<StaffEntity> findByIdForUpdate(@Param("id") Long id);

    Optional<StaffEntity> findByBadgeNumber(String badgeNumber);

    List<StaffEntity> findByStaffAvailability(StaffAvailability availability);
}
