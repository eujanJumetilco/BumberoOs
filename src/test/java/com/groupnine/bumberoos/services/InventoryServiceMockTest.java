package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.ItemEntity;
import com.groupnine.bumberoos.domain.enums.ItemCondition;
import com.groupnine.bumberoos.domain.exceptions.InvalidInventoryExceptions;
import com.groupnine.bumberoos.domain.enums.ItemCategory;
import com.groupnine.bumberoos.domain.exceptions.BadRequestException;
import com.groupnine.bumberoos.domain.exceptions.ConflictException;
import com.groupnine.bumberoos.repositories.IncidentItemAssignmentRepository;
import com.groupnine.bumberoos.repositories.ItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests for InventoryService with a mocked ItemRepository (no database). */
@ExtendWith(MockitoExtension.class)
class InventoryServiceMockTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private IncidentItemAssignmentRepository assignmentRepository;

    @InjectMocks
    private InventoryService service;

    private ItemEntity mockItem(long id, int qty) {
        ItemEntity item = new ItemEntity(ItemCondition.GOOD, "fire hose", "30 meter hose", qty);
        item.setId(id);
        return item;
    }

    @Test
    void addItem_clearsIdAndSaves() throws Exception {
        ItemEntity input = mockItem(55L, 5);
        when(itemRepository.save(any(ItemEntity.class))).thenAnswer(i -> i.getArgument(0));

        ItemEntity result = service.addItem(input);

        assertNull(result.getId());
        assertEquals("Fire Hose", result.getName());
        verify(itemRepository).save(input);
    }

    @Test
    void addItem_allowsZeroQuantity() throws Exception {
        when(itemRepository.save(any(ItemEntity.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(0, service.addItem(mockItem(0L, 0)).getQuantity());
    }

    @Test
    void addItem_rejectsBlankNameWithoutTouchingRepository() {
        ItemEntity blank = new ItemEntity(ItemCondition.GOOD, "   ", "desc", 1);

        assertThrows(InvalidInventoryExceptions.class, () -> service.addItem(blank));
        verifyNoInteractions(itemRepository);
    }

    @Test
    void addItem_rejectsNegativeQuantityWithoutTouchingRepository() {
        ItemEntity negative = new ItemEntity(ItemCondition.GOOD, "hose", "desc", -1);

        assertThrows(InvalidInventoryExceptions.class, () -> service.addItem(negative));
        verifyNoInteractions(itemRepository);
    }

    @Test
    void removeItem_deletesWhenExists_andReturnsFalseWhenMissing() {
        when(itemRepository.findById(1L)).thenReturn(Optional.of(mockItem(1L, 5)));
        when(itemRepository.findById(2L)).thenReturn(Optional.empty());

        assertTrue(service.removeItem(1L));
        assertFalse(service.removeItem(2L));
        verify(itemRepository).deleteById(1L);
        verify(itemRepository, never()).deleteById(2L);
    }

    @Test
    void editItem_updatesProvidedFields() {
        ItemEntity existing = mockItem(1L, 5);
        LocalDate today = LocalDate.of(2026, 3, 1);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(itemRepository.save(existing)).thenReturn(existing);

        ItemEntity edited = service.editItem(
                1L, "rescue hose", "updated", 12, ItemCondition.DAMAGED, today, null, null
        ).orElseThrow();

        assertEquals("Rescue Hose", edited.getName());
        assertEquals("Updated", edited.getDescription());
        assertEquals(12, edited.getQuantity());
        assertEquals(ItemCondition.DAMAGED, edited.getItemCondition());
        assertEquals(today, edited.getLastMaintained());
    }

    @Test
    void editItem_allowsSettingQuantityToZero() {
        ItemEntity existing = mockItem(1L, 5);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(itemRepository.save(existing)).thenReturn(existing);

        assertEquals(0, service.editItem(1L, null, null, 0, null, null, null, null).orElseThrow().getQuantity());
    }

    @Test
    void editItem_returnsEmptyAndDoesNotSaveWhenMissing() {
        when(itemRepository.findById(404L)).thenReturn(Optional.empty());

        assertTrue(service.editItem(404L, "x", null, null, null, null, null, null).isEmpty());
        verify(itemRepository, never()).save(any());
    }

    @Test
    void increaseQuantity_addsToExisting() {
        ItemEntity existing = mockItem(1L, 5);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(itemRepository.save(existing)).thenReturn(existing);

        assertEquals(15, service.increaseQuantity(1L, 10).orElseThrow().getQuantity());
    }

    @Test
    void increaseQuantity_returnsEmptyWhenMissing() {
        when(itemRepository.findById(404L)).thenReturn(Optional.empty());

        assertTrue(service.increaseQuantity(404L, 3).isEmpty());
        verify(itemRepository, never()).save(any());
    }

    @Test
    void findByName_delegatesAndHandlesNull() {
        ItemEntity item = mockItem(1L, 5);
        when(itemRepository.findFirstByNameIgnoreCase("FIRE HOSE")).thenReturn(Optional.of(item));

        assertSame(item, service.findByName("FIRE HOSE").orElseThrow());
        assertTrue(service.findByName(null).isEmpty());
        verify(itemRepository, never()).findFirstByNameIgnoreCase(null);
    }

    @Test
    void findById_andGetAll_delegateToRepository() {
        ItemEntity item = mockItem(1L, 5);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemRepository.findAll()).thenReturn(List.of(item, mockItem(2L, 1)));

        assertSame(item, service.findById(1L).orElseThrow());
        assertEquals(2, service.getAllItems().size());
    }

    // ===== DISPATCH-AWARE RULES ===== //

    @Test
    void addItem_resetsDispatchedAndDefaultsCategory() throws Exception {
        ItemEntity input = mockItem(1L, 5);
        input.setQuantityDispatched(3);
        input.setCategory(null);
        when(itemRepository.save(any(ItemEntity.class))).thenAnswer(i -> i.getArgument(0));

        ItemEntity result = service.addItem(input);

        assertEquals(0, result.getQuantityDispatched());
        assertEquals(ItemCategory.OTHER, result.getCategory());
    }

    @Test
    void removeItem_blockedWhenItHasAssignmentHistory() {
        when(itemRepository.findById(1L)).thenReturn(Optional.of(mockItem(1L, 5)));
        when(assignmentRepository.existsByItemId(1L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.removeItem(1L));
        verify(itemRepository, never()).deleteById(any());
    }

    @Test
    void editItem_rejectsQuantityBelowDispatched() {
        ItemEntity existing = mockItem(1L, 10);
        existing.setQuantityDispatched(4);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class,
                () -> service.editItem(1L, null, null, 3, null, null, null, null));
        verify(itemRepository, never()).save(any());
    }

    @Test
    void editItem_setsCategoryAndSerialNumber() {
        ItemEntity existing = mockItem(1L, 1);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(itemRepository.save(existing)).thenReturn(existing);

        ItemEntity edited = service.editItem(1L, null, null, null, null, null, ItemCategory.ENGINE, " ABC 123 ")
                .orElseThrow();

        assertEquals(ItemCategory.ENGINE, edited.getCategory());
        assertEquals("ABC 123", edited.getSerialNumber());
    }

    @Test
    void increaseQuantity_rejectsResultBelowZero() {
        when(itemRepository.findById(1L)).thenReturn(Optional.of(mockItem(1L, 5)));

        assertThrows(BadRequestException.class, () -> service.increaseQuantity(1L, -6));
    }

    @Test
    void increaseQuantity_rejectsResultBelowDispatched() {
        ItemEntity existing = mockItem(1L, 10);
        existing.setQuantityDispatched(8);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class, () -> service.increaseQuantity(1L, -3));
        verify(itemRepository, never()).save(any());
    }

    @Test
    void getItems_choosesRepositoryQueryByFilters() {
        service.getItems(null, false);
        service.getItems(ItemCategory.ENGINE, false);
        service.getItems(null, true);
        service.getItems(ItemCategory.SCBA, true);

        verify(itemRepository).findAll();
        verify(itemRepository).findByCategory(ItemCategory.ENGINE);
        verify(itemRepository).findWithAvailableUnits();
        verify(itemRepository).findWithAvailableUnitsByCategory(ItemCategory.SCBA);
    }
}
