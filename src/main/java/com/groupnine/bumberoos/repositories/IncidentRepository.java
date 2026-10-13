package com.groupnine.bumberoos.repositories;

import com.groupnine.bumberoos.domain.entities.IncidentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<IncidentEntity, Long> {
}
