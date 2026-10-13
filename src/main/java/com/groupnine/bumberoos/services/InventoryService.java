package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.exceptions.InvalidInventoryExceptions;

import com.groupnine.bumberoos.domain.entities.ItemEntity;
import org.springframework.stereotype.Service;
import com.groupnine.bumberoos.util.TextStorageUtil;

import java.time.LocalDate;
import java.util.ArrayList;


@Service
public class InventoryService {

    private static final String FILE_NAME = "data/inventory_data.txt";

    private final ArrayList<ItemEntity> inventory = new ArrayList<>();
    private int nextId = 1;

    public InventoryService() {
        ArrayList<ItemEntity> loaded = TextStorageUtil.loadInventoryFromTextFile(FILE_NAME);
        inventory.addAll(loaded);

        for (ItemEntity i : inventory) {
            if (i.getId() >= nextId) {
                nextId = i.getId() + 1;
            }
        }
    }

    public void saveAll() {
        TextStorageUtil.saveInventoryToTextFile(inventory, FILE_NAME);
    }

    public int getNextId() {
        return nextId;
    }

    public ItemEntity addItem(ItemEntity item) throws InvalidInventoryExceptions {

        if (item.getName() == null || item.getName().isBlank()) {
            throw new InvalidInventoryExceptions(
                "Item name cannot be blank."
            );
        }

        if (item.getQuantity() < 0) {
            throw new InvalidInventoryExceptions(
                "Quantity cannot be negative."
            );
        }

        item.setId(nextId++);
        inventory.add(item);
        saveAll();

        return item;
    }

    public boolean removeItem(int id) {
        boolean removed = inventory.removeIf(i -> i.getId() == id);

        if (removed) {
            saveAll();
        }

        return removed;
    }

    public ItemEntity editItem(
            int id,
            String name,
            String description,
            int quantity,
            int conditionCode,
            LocalDate lastMaintained
    ) {
        ItemEntity item = findById(id);
        if (item == null) return null;

        if (name          != null) item.setName(name);
        if (description   != null) item.setDescription(description);
        if (quantity      >= 0)    item.setQuantity(quantity);
        if (conditionCode >= 0)    item.setItemCondition(conditionCode);
        if (lastMaintained!= null) item.setLastMaintained(lastMaintained);

        saveAll();
        return item;
    }

    public ItemEntity findById(int id) {
        return inventory.stream()
                .filter(i -> i.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public ItemEntity findByName(String name) {
        if (name == null) {
            return null;
        }
        return inventory.stream()
                .filter(i -> i.getName() != null && i.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public ItemEntity increaseQuantity(int id, int additionalQty) {
        ItemEntity item = findById(id);
        if (item == null) {
            return null;
        }
        item.setQuantity(item.getQuantity() + additionalQty);
        saveAll();
        return item;
    }

    public ArrayList<ItemEntity> getAllItems() {
        return new ArrayList<>(inventory);
    }
}