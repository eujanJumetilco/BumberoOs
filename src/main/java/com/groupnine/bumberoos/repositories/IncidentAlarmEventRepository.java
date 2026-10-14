package com.groupnine.bumberoos.repositories;

import com.groupnine.bumberoos.domain.entities.IncidentAlarmEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentAlarmEventRepository extends JpaRepository<IncidentAlarmEvent, Long> {
}
