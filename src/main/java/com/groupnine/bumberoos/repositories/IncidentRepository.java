package com.groupnine.bumberoos.repositories;

import com.groupnine.bumberoos.domain.entities.IncidentEntity;
import com.groupnine.bumberoos.domain.enums.IncidentNature;
import com.groupnine.bumberoos.domain.enums.IncidentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<IncidentEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select x from IncidentEntity x where x.id = :id")
    Optional<IncidentEntity> findByIdForUpdate(@Param("id") Long id);

    List<IncidentEntity> findByStatus(IncidentStatus status);

    List<IncidentEntity> findByNature(IncidentNature nature);

    List<IncidentEntity> findByStatusAndNature(IncidentStatus status, IncidentNature nature);
}
