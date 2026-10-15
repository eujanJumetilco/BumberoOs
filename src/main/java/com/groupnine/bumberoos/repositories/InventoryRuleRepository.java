package com.groupnine.bumberoos.repositories;

import com.groupnine.bumberoos.domain.entities.InventoryRuleEntity;
import com.groupnine.bumberoos.domain.enums.ItemCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryRuleRepository extends JpaRepository<InventoryRuleEntity, Long> {

    Optional<InventoryRuleEntity> findByCategory(ItemCategory category);
}
