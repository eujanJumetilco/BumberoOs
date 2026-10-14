package com.groupnine.bumberoos.repositories;

import com.groupnine.bumberoos.domain.entities.IncidentStaffAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentStaffAssignmentRepository extends JpaRepository<IncidentStaffAssignment, Long> {

    Optional<IncidentStaffAssignment> findByIncidentIdAndStaffIdAndReleasedAtIsNull(Long incidentId, Long staffId);

    List<IncidentStaffAssignment> findByIncidentIdAndReleasedAtIsNull(Long incidentId);

    boolean existsByStaffId(Long staffId);

    boolean existsByStaffIdAndReleasedAtIsNull(Long staffId);
}
