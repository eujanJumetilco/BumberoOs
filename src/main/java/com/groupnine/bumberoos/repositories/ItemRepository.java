package com.groupnine.bumberoos.repositories;

import com.groupnine.bumberoos.domain.entities.ItemEntity;
import com.groupnine.bumberoos.domain.enums.ItemCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<ItemEntity, Long> {

    Optional<ItemEntity> findFirstByNameIgnoreCase(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select x from ItemEntity x where x.id = :id")
    Optional<ItemEntity> findByIdForUpdate(@Param("id") Long id);

    List<ItemEntity> findByCategory(ItemCategory category);

    @Query("select i from ItemEntity i where i.quantity > i.quantityDispatched "
            + "and i.itemCondition = com.groupnine.bumberoos.domain.enums.ItemCondition.GOOD")
    List<ItemEntity> findWithAvailableUnits();

    @Query("select i from ItemEntity i where i.category = :category and i.quantity > i.quantityDispatched "
            + "and i.itemCondition = com.groupnine.bumberoos.domain.enums.ItemCondition.GOOD")
    List<ItemEntity> findWithAvailableUnitsByCategory(@Param("category") ItemCategory category);
}
