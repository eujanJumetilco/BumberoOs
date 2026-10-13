package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.ItemEntity;
import com.groupnine.bumberoos.domain.enums.ItemCondition;
import com.groupnine.bumberoos.domain.exceptions.InvalidInventoryExceptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class InventoryServiceTest {

    @Autowired
    private InventoryService service;

    // Helper: adds a GOOD item with quantity 5
    private ItemEntity addSample() throws InvalidInventoryExceptions {
        return service.addItem(
                new ItemEntity(ItemCondition.GOOD, "fire hose", "30 meter hose", 5)
        );
    }

    // ===== ADD ===== //

    @Test
    void addItem_assignsIdAndStoresIt() throws Exception {
        ItemEntity added = addSample();

        assertNotNull(added.getId());
        assertEquals("Fire Hose", added.getName());
        assertEquals(ItemCondition.GOOD, added.getItemCondition());
        assertEquals(5, added.getQuantity());
        assertTrue(service.findById(added.getId()).isPresent());
    }

    @Test
    void addItem_throwsOnBlankName() {
        int before = service.getAllItems().size();

        assertThrows(InvalidInventoryExceptions.class, () ->
                service.addItem(new ItemEntity(ItemCondition.GOOD, "   ", "blank name", 1)));

        assertEquals(before, service.getAllItems().size());
    }

    @Test
    void addItem_throwsOnNegativeQuantity() {
        int before = service.getAllItems().size();

        assertThrows(InvalidInventoryExceptions.class, () ->
                service.addItem(new ItemEntity(ItemCondition.GOOD, "bad qty", "negative", -5)));

        assertEquals(before, service.getAllItems().size());
    }

    @Test
    void addItem_allowsZeroQuantity() throws Exception {
        ItemEntity added = service.addItem(
                new ItemEntity(ItemCondition.GOOD, "empty stock", "none left", 0));

        assertEquals(0, added.getQuantity());
    }

    // ===== FIND ===== //

    @Test
    void findById_returnsEmptyForUnknownId() {
        assertTrue(service.findById(-999L).isEmpty());
    }

    @Test
    void findByName_isCaseInsensitive() throws Exception {
        ItemEntity added = addSample();

        ItemEntity found = service.findByName("FIRE HOSE").orElseThrow();

        assertEquals(added.getId(), found.getId());
    }

    @Test
    void findByName_returnsEmptyForUnknownOrNullName() {
        assertTrue(service.findByName("definitely not an item").isEmpty());
        assertTrue(service.findByName(null).isEmpty());
    }

    // ===== EDIT ===== //

    @Test
    void editItem_updatesProvidedFields() throws Exception {
        ItemEntity added = addSample();
        LocalDate today = LocalDate.now();

        ItemEntity edited = service.editItem(
                added.getId(), "rescue hose", "updated description", 10,
                ItemCondition.DAMAGED, today
        ).orElseThrow();

        assertEquals("Rescue Hose", edited.getName());
        assertEquals("Updated description", edited.getDescription());
        assertEquals(10, edited.getQuantity());
        assertEquals(ItemCondition.DAMAGED, edited.getItemCondition());
        assertEquals(today, edited.getLastMaintained());
    }

    @Test
    void editItem_keepsFieldsWhenNull() throws Exception {
        ItemEntity added = addSample();
        String originalName = added.getName();
        String originalDescription = added.getDescription();

        ItemEntity edited = service.editItem(added.getId(), null, null, null, null, null)
                .orElseThrow();

        assertEquals(originalName, edited.getName());
        assertEquals(originalDescription, edited.getDescription());
        assertEquals(5, edited.getQuantity());
        assertEquals(ItemCondition.GOOD, edited.getItemCondition());
        assertNull(edited.getLastMaintained());
    }

    @Test
    void editItem_returnsEmptyForUnknownId() {
        assertTrue(service.editItem(-999L, null, null, null, null, null).isEmpty());
    }

    // ===== INCREASE QUANTITY ===== //

    @Test
    void increaseQuantity_addsToExistingQuantity() throws Exception {
        ItemEntity added = addSample();

        assertEquals(10, service.increaseQuantity(added.getId(), 5).orElseThrow().getQuantity());
    }

    @Test
    void increaseQuantity_returnsEmptyForUnknownId() {
        assertTrue(service.increaseQuantity(-999L, 5).isEmpty());
    }

    // ===== GET ALL / REMOVE ===== //

    @Test
    void getAllItems_containsAdded() throws Exception {
        ItemEntity added = addSample();

        assertTrue(service.getAllItems().stream()
                .anyMatch(i -> i.getId().equals(added.getId())));
    }

    @Test
    void removeItem_removesExistingAndReturnsFalseAfterwards() throws Exception {
        ItemEntity added = addSample();

        assertTrue(service.removeItem(added.getId()));
        assertTrue(service.findById(added.getId()).isEmpty());
        assertFalse(service.removeItem(added.getId()));
    }

    @Test
    void removeItem_returnsFalseForUnknownId() {
        assertFalse(service.removeItem(-999L));
    }
}
