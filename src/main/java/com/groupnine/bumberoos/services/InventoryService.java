package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.ItemEntity;
import com.groupnine.bumberoos.domain.enums.ItemCondition;
import com.groupnine.bumberoos.domain.exceptions.InvalidInventoryExceptions;
import com.groupnine.bumberoos.repositories.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(rollbackFor = Exception.class)
public class InventoryService {

    private final ItemRepository itemRepository;

    public InventoryService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    public ItemEntity addItem(ItemEntity item) throws InvalidInventoryExceptions {
        if (item.getName() == null || item.getName().isBlank()) {
            throw new InvalidInventoryExceptions("Item name cannot be blank.");
        }
        if (item.getQuantity() < 0) {
            throw new InvalidInventoryExceptions("Quantity cannot be negative.");
        }

        item.setId(null);
        return itemRepository.save(item);
    }

    /** @return true if found and removed, false otherwise. */
    public boolean removeItem(Long id) {
        if (!itemRepository.existsById(id)) return false;
        itemRepository.deleteById(id);
        return true;
    }

    /** Null arguments leave the existing value unchanged. */
    public Optional<ItemEntity> editItem(
            Long id,
            String name,
            String description,
            Integer quantity,
            ItemCondition condition,
            LocalDate lastMaintained
    ) {
        return itemRepository.findById(id).map(item -> {
            if (name != null) item.setName(name);
            if (description != null) item.setDescription(description);
            if (quantity != null) item.setQuantity(quantity);
            if (condition != null) item.setItemCondition(condition);
            if (lastMaintained != null) item.setLastMaintained(lastMaintained);
            item.normalize();
            return itemRepository.save(item);
        });
    }

    @Transactional(readOnly = true)
    public Optional<ItemEntity> findById(Long id) {
        return itemRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<ItemEntity> findByName(String name) {
        if (name == null) return Optional.empty();
        return itemRepository.findFirstByNameIgnoreCase(name);
    }

    public Optional<ItemEntity> increaseQuantity(Long id, int additionalQty) {
        return itemRepository.findById(id).map(item -> {
            item.setQuantity(item.getQuantity() + additionalQty);
            item.normalize();
            return itemRepository.save(item);
        });
    }

    @Transactional(readOnly = true)
    public List<ItemEntity> getAllItems() {
        return itemRepository.findAll();
    }
}
