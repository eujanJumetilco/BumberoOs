package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.ItemEntity;
import com.groupnine.bumberoos.domain.enums.ItemCondition;
import com.groupnine.bumberoos.domain.exceptions.InvalidInventoryExceptions;
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
        when(itemRepository.existsById(1L)).thenReturn(true);
        when(itemRepository.existsById(2L)).thenReturn(false);

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
                1L, "rescue hose", "updated", 12, ItemCondition.DAMAGED, today
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

        assertEquals(0, service.editItem(1L, null, null, 0, null, null).orElseThrow().getQuantity());
    }

    @Test
    void editItem_returnsEmptyAndDoesNotSaveWhenMissing() {
        when(itemRepository.findById(404L)).thenReturn(Optional.empty());

        assertTrue(service.editItem(404L, "x", null, null, null, null).isEmpty());
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
}
