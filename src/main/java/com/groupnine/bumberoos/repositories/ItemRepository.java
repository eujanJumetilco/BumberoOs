package com.groupnine.bumberoos.repositories;

import com.groupnine.bumberoos.domain.entities.ItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ItemRepository extends JpaRepository<ItemEntity, Long> {

    Optional<ItemEntity> findFirstByNameIgnoreCase(String name);
}
