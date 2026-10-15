package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.ItemEntity;
import com.groupnine.bumberoos.domain.enums.ItemCategory;
import com.groupnine.bumberoos.domain.enums.ItemCondition;
import com.groupnine.bumberoos.domain.exceptions.BadRequestException;
import com.groupnine.bumberoos.domain.exceptions.ConflictException;
import com.groupnine.bumberoos.domain.exceptions.InvalidInventoryExceptions;
import com.groupnine.bumberoos.repositories.IncidentItemAssignmentRepository;
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
    private final IncidentItemAssignmentRepository assignmentRepository;

    public InventoryService(ItemRepository itemRepository, IncidentItemAssignmentRepository assignmentRepository) {
        this.itemRepository = itemRepository;
        this.assignmentRepository = assignmentRepository;
    }

    public ItemEntity addItem(ItemEntity item) throws InvalidInventoryExceptions {
        if (item.getName() == null || item.getName().isBlank()) {
            throw new InvalidInventoryExceptions("Item name cannot be blank.");
        }
        if (item.getQuantity() < 0) {
            throw new InvalidInventoryExceptions("Quantity cannot be negative.");
        }

        item.setId(null);
        item.setQuantityDispatched(0);
        if (item.getCategory() == null) item.setCategory(ItemCategory.OTHER);
        return itemRepository.save(item);
    }

    /**
     * @return true if found and removed, false if not found.
     * @throws ConflictException if the item has any assignment history.
     */
    public boolean removeItem(Long id) {
        Optional<ItemEntity> found = itemRepository.findById(id);
        if (found.isEmpty()) return false;

        if (assignmentRepository.existsByItemId(id)) {
            throw new ConflictException(found.get().getName() + " has incident assignment history and cannot be "
                    + "deleted; set its condition to RETIRED instead");
        }
        itemRepository.deleteById(id);
        return true;
    }

    /**
     * Null arguments leave the existing value unchanged.
     *
     * @throws ConflictException if {@code quantity} is below the units currently dispatched.
     */
    public Optional<ItemEntity> editItem(
            Long id,
            String name,
            String description,
            Integer quantity,
            ItemCondition condition,
            LocalDate lastMaintained,
            ItemCategory category,
            String serialNumber
    ) {
        return editItem(id, name, description, quantity, condition, lastMaintained, category, serialNumber, null);
    }

    /** As above, plus the expiry date (null leaves it unchanged). */
    public Optional<ItemEntity> editItem(
            Long id,
            String name,
            String description,
            Integer quantity,
            ItemCondition condition,
            LocalDate lastMaintained,
            ItemCategory category,
            String serialNumber,
            LocalDate expiryDate
    ) {
        return itemRepository.findById(id).map(item -> {
            if (quantity != null && quantity < item.getQuantityDispatched()) {
                throw new ConflictException(item.getName() + " has " + item.getQuantityDispatched()
                        + " unit(s) dispatched; quantity cannot be reduced below that");
            }
            if (name != null) item.setName(name);
            if (description != null) item.setDescription(description);
            if (quantity != null) item.setQuantity(quantity);
            if (condition != null) item.setItemCondition(condition);
            if (lastMaintained != null) item.setLastMaintained(lastMaintained);
            if (category != null) item.setCategory(category);
            if (serialNumber != null) item.setSerialNumber(serialNumber);
            if (expiryDate != null) item.setExpiryDate(expiryDate);
            item.normalize();
            return itemRepository.save(item);
        });
    }

    @Transactional(readOnly = true)
    public Optional<ItemEntity> findById(Long id) {
        return itemRepository.findById(id);
    }

    /** Loads with a pessimistic write lock; used by dispatching. */
    public Optional<ItemEntity> findByIdForUpdate(Long id) {
        return itemRepository.findByIdForUpdate(id);
    }

    @Transactional(readOnly = true)
    public Optional<ItemEntity> findByName(String name) {
        if (name == null) return Optional.empty();
        return itemRepository.findFirstByNameIgnoreCase(name);
    }

    /**
     * @throws BadRequestException if the resulting quantity would be negative
     * @throws ConflictException   if it would drop below the units currently dispatched
     */
    public Optional<ItemEntity> increaseQuantity(Long id, int additionalQty) {
        return itemRepository.findById(id).map(item -> {
            int newQuantity = item.getQuantity() + additionalQty;
            if (newQuantity < 0) {
                throw new BadRequestException("Quantity of " + item.getName() + " cannot go below zero.");
            }
            if (newQuantity < item.getQuantityDispatched()) {
                throw new ConflictException(item.getName() + " has " + item.getQuantityDispatched()
                        + " unit(s) dispatched; quantity cannot be reduced below that");
            }
            item.setQuantity(newQuantity);
            item.normalize();
            return itemRepository.save(item);
        });
    }

    @Transactional(readOnly = true)
    public List<ItemEntity> getAllItems() {
        return itemRepository.findAll();
    }

    /** Both filters are optional. {@code availableOnly} keeps GOOD items with at least one free unit. */
    @Transactional(readOnly = true)
    public List<ItemEntity> getItems(ItemCategory category, boolean availableOnly) {
        if (availableOnly) {
            return category == null
                    ? itemRepository.findWithAvailableUnits()
                    : itemRepository.findWithAvailableUnitsByCategory(category);
        }
        return category == null ? itemRepository.findAll() : itemRepository.findByCategory(category);
    }
}
