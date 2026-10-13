package com.groupnine.bumberoos.repositories;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRepository extends JpaRepository<StaffEntity, Long> {
}
