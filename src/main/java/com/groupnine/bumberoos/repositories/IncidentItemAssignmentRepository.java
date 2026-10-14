package com.groupnine.bumberoos.repositories;

import com.groupnine.bumberoos.domain.entities.IncidentItemAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentItemAssignmentRepository extends JpaRepository<IncidentItemAssignment, Long> {

    Optional<IncidentItemAssignment> findByIncidentIdAndItemIdAndReleasedAtIsNull(Long incidentId, Long itemId);

    List<IncidentItemAssignment> findByIncidentIdAndReleasedAtIsNull(Long incidentId);

    boolean existsByItemId(Long itemId);

    boolean existsByItemIdAndReleasedAtIsNull(Long itemId);
}
