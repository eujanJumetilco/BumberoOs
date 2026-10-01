package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.ItemEntity;
import com.groupnine.bumberoos.domain.enums.ItemCondition;
import com.groupnine.bumberoos.domain.exceptions.InvalidInventoryExceptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class InventoryServiceTest {

    private InventoryService service;
    private final ArrayList<Integer> createdIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new InventoryService();
        createdIds.clear();
    }

    @AfterEach
    void tearDown() {
        for (int id : createdIds) {
            service.removeItem(id);
        }
    }

    // Helper: adds a GOOD item with quantity 5 and tracks it for cleanup
    private ItemEntity addSample() throws InvalidInventoryExceptions {
        ItemEntity added = service.addItem(
                new ItemEntity(0, 0, "fire hose", "30 meter hose", 5)
        );
        createdIds.add(added.getId());
        return added;
    }

    // ===== ADD ===== //

    @Test
    void addItem_assignsIdAndStoresIt() throws Exception {
        int expectedId = service.getNextId();

        ItemEntity added = addSample();

        assertEquals(expectedId, added.getId());
        assertEquals(expectedId + 1, service.getNextId());
        assertEquals(ItemCondition.GOOD, added.getItemCondition());
        assertEquals(5, added.getQuantity());
        assertSame(added, service.findById(added.getId()));
    }

    @Test
    void addItem_throwsOnBlankName() {
        int before = service.getAllItems().size();

        assertThrows(InvalidInventoryExceptions.class, () ->
                service.addItem(new ItemEntity(0, 0, "   ", "blank name", 1)));

        assertEquals(before, service.getAllItems().size());
    }

    @Test
    void addItem_throwsOnNegativeQuantity() {
        int before = service.getAllItems().size();

        assertThrows(InvalidInventoryExceptions.class, () ->
                service.addItem(new ItemEntity(0, 0, "bad qty", "negative", -5)));

        assertEquals(before, service.getAllItems().size());
    }

    @Test
    void addItem_allowsZeroQuantity() throws Exception {
        ItemEntity added = service.addItem(new ItemEntity(0, 0, "empty stock", "none left", 0));
        createdIds.add(added.getId());

        assertEquals(0, added.getQuantity());
    }

    // ===== FIND ===== //

    @Test
    void findById_returnsNullForUnknownId() {
        assertNull(service.findById(-999));
    }

    @Test
    void findByName_isCaseInsensitive() throws Exception {
        ItemEntity added = addSample();

        ItemEntity found = service.findByName("FIRE HOSE");

        assertNotNull(found);
        assertEquals(added.getId(), found.getId());
    }

    @Test
    void findByName_returnsNullForUnknownOrNullName() {
        assertNull(service.findByName("definitely not an item"));
        assertNull(service.findByName(null));
    }

    // ===== EDIT ===== //

    @Test
    void editItem_updatesProvidedFields() throws Exception {
        ItemEntity added = addSample();
        LocalDate today = LocalDate.now();

        ItemEntity edited = service.editItem(
                added.getId(), "rescue hose", "updated description", 10, 1, today
        );

        assertNotNull(edited);
        assertTrue(edited.getName().equalsIgnoreCase("rescue hose"));
        assertTrue(edited.getDescription().equalsIgnoreCase("updated description"));
        assertEquals(10, edited.getQuantity());
        assertEquals(ItemCondition.DAMAGED, edited.getItemCondition());
        assertEquals(today, edited.getLastMaintained());
    }

    @Test
    void editItem_keepsFieldsWhenNullOrMinusOne() throws Exception {
        ItemEntity added = addSample();
        String originalName = added.getName();
        String originalDescription = added.getDescription();

        ItemEntity edited = service.editItem(added.getId(), null, null, -1, -1, null);

        assertEquals(originalName, edited.getName());
        assertEquals(originalDescription, edited.getDescription());
        assertEquals(5, edited.getQuantity());
        assertEquals(ItemCondition.GOOD, edited.getItemCondition());
        assertNull(edited.getLastMaintained());
    }

    @Test
    void editItem_returnsNullForUnknownId() {
        assertNull(service.editItem(-999, null, null, -1, -1, null));
    }

    // ===== INCREASE QUANTITY ===== //

    @Test
    void increaseQuantity_addsToExistingQuantity() throws Exception {
        ItemEntity added = addSample();

        ItemEntity updated = service.increaseQuantity(added.getId(), 5);

        assertEquals(10, updated.getQuantity());
    }

    @Test
    void increaseQuantity_returnsNullForUnknownId() {
        assertNull(service.increaseQuantity(-999, 5));
    }

    // ===== GET ALL ===== //

    @Test
    void getAllItems_containsAddedAndReturnsCopy() throws Exception {
        ItemEntity added = addSample();

        ArrayList<ItemEntity> all = service.getAllItems();
        assertTrue(all.contains(added));

        all.clear();
        assertNotNull(service.findById(added.getId()));
    }

    // ===== REMOVE ===== //

    @Test
    void removeItem_removesExistingAndReturnsFalseAfterwards() throws Exception {
        ItemEntity added = addSample();

        assertTrue(service.removeItem(added.getId()));
        assertNull(service.findById(added.getId()));
        assertFalse(service.removeItem(added.getId()));
    }

    @Test
    void removeItem_returnsFalseForUnknownId() {
        assertFalse(service.removeItem(-999));
    }

    // ===== PERSISTENCE ===== //

    @Test
    void saveAll_persistsToFile() throws Exception {
        ItemEntity added = addSample();

        InventoryService reloaded = new InventoryService();
        ItemEntity persisted = reloaded.findById(added.getId());

        assertNotNull(persisted);
        assertEquals(added.getQuantity(), persisted.getQuantity());
        assertEquals(added.getItemCondition(), persisted.getItemCondition());
    }
}