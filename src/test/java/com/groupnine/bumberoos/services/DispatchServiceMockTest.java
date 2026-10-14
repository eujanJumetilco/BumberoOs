package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.*;
import com.groupnine.bumberoos.domain.enums.*;
import com.groupnine.bumberoos.domain.exceptions.BadRequestException;
import com.groupnine.bumberoos.domain.exceptions.ConflictException;
import com.groupnine.bumberoos.domain.exceptions.ResourceNotFoundException;
import com.groupnine.bumberoos.repositories.IncidentItemAssignmentRepository;
import com.groupnine.bumberoos.repositories.IncidentRepository;
import com.groupnine.bumberoos.repositories.IncidentStaffAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Rule-level unit tests for DispatchService with all collaborators mocked (no database). */
@ExtendWith(MockitoExtension.class)
class DispatchServiceMockTest {

    @Mock IncidentRepository incidentRepository;
    @Mock IncidentStaffAssignmentRepository staffAssignments;
    @Mock IncidentItemAssignmentRepository itemAssignments;
    @Mock StaffService staffService;
    @Mock InventoryService inventoryService;

    @InjectMocks DispatchService service;

    private IncidentEntity incident(long id, IncidentStatus status) {
        IncidentEntity i = new IncidentEntity(IncidentNature.EMERGENCY, "fire", "zone 3", LocalDateTime.now(), "juan");
        i.setId(id);
        i.setStatus(status);
        return i;
    }

    private StaffEntity staff(long id, StaffAvailability availability, boolean employed) {
        StaffEntity s = new StaffEntity("ana reyes", "volunteer", null, null, employed, null, null);
        s.setId(id);
        s.setStaffAvailability(availability);
        return s;
    }

    private ItemEntity item(long id, int quantity, int dispatched, ItemCondition condition) {
        ItemEntity i = new ItemEntity(condition, "fire suit", null, quantity);
        i.setId(id);
        i.setQuantityDispatched(dispatched);
        return i;
    }

    private IncidentEntity lockIncident(long id, IncidentStatus status) {
        IncidentEntity i = incident(id, status);
        when(incidentRepository.findByIdForUpdate(id)).thenReturn(Optional.of(i));
        return i;
    }

    // ===== staff ===== //

    @Test
    void assignStaff_marksStaffDispatchedAndStoresAssignment() {
        IncidentEntity i = lockIncident(1L, IncidentStatus.ONGOING);
        StaffEntity s = staff(2L, StaffAvailability.AVAILABLE, true);
        when(staffService.findByIdForUpdate(2L)).thenReturn(Optional.of(s));
        when(staffAssignments.findByIncidentIdAndStaffIdAndReleasedAtIsNull(1L, 2L)).thenReturn(Optional.empty());

        service.assignStaff(1L, 2L, "driver");

        assertEquals(StaffAvailability.DISPATCHED, s.getStaffAvailability());
        assertEquals(1, i.getStaffAssignments().size());
        assertEquals("Driver", i.getStaffAssignments().get(0).getRole());
        verify(staffAssignments).save(any(IncidentStaffAssignment.class));
    }

    @Test
    void assignStaff_namesTheReasonInTheConflictMessage() {
        lockIncident(1L, IncidentStatus.ONGOING);
        when(staffService.findByIdForUpdate(2L)).thenReturn(Optional.of(staff(2L, StaffAvailability.DISPATCHED, true)));
        when(staffService.findByIdForUpdate(3L)).thenReturn(Optional.of(staff(3L, StaffAvailability.AVAILABLE, false)));

        assertEquals("Ana Reyes is DISPATCHED",
                assertThrows(ConflictException.class, () -> service.assignStaff(1L, 2L, null)).getMessage());
        assertEquals("Ana Reyes is not employed",
                assertThrows(ConflictException.class, () -> service.assignStaff(1L, 3L, null)).getMessage());
        verify(staffAssignments, never()).save(any());
    }

    @Test
    void assignStaff_existingActiveAssignmentOnlyUpdatesRole() {
        IncidentEntity i = lockIncident(1L, IncidentStatus.ONGOING);
        StaffEntity s = staff(2L, StaffAvailability.DISPATCHED, true);
        IncidentStaffAssignment existing = new IncidentStaffAssignment(i, s, null, LocalDateTime.now());
        when(staffService.findByIdForUpdate(2L)).thenReturn(Optional.of(s));
        when(staffAssignments.findByIncidentIdAndStaffIdAndReleasedAtIsNull(1L, 2L)).thenReturn(Optional.of(existing));

        service.assignStaff(1L, 2L, "nozzleman");

        assertEquals("Nozzleman", existing.getRole());
        verify(staffAssignments, never()).save(any());
    }

    @Test
    void assignStaff_resolvedIncidentIsRejectedBeforeLockingStaff() {
        lockIncident(1L, IncidentStatus.RESOLVED);

        assertThrows(ConflictException.class, () -> service.assignStaff(1L, 2L, null));
        verifyNoInteractions(staffService);
    }

    @Test
    void assignStaff_unknownIncidentIs404() {
        when(incidentRepository.findByIdForUpdate(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.assignStaff(9L, 2L, null));
    }

    @Test
    void releaseStaff_withoutActiveAssignmentIsNoOp() {
        lockIncident(1L, IncidentStatus.ONGOING);
        when(staffAssignments.findByIncidentIdAndStaffIdAndReleasedAtIsNull(1L, 2L)).thenReturn(Optional.empty());

        service.releaseStaff(1L, 2L);

        verifyNoInteractions(staffService);
    }

    // ===== items ===== //

    @Test
    void setItemQuantity_deltaMathAgainstExistingAssignment() {
        IncidentEntity i = lockIncident(1L, IncidentStatus.ONGOING);
        ItemEntity it = item(5L, 10, 4, ItemCondition.GOOD);
        IncidentItemAssignment existing = new IncidentItemAssignment(i, it, 4, LocalDateTime.now());
        when(inventoryService.findByIdForUpdate(5L)).thenReturn(Optional.of(it));
        when(itemAssignments.findByIncidentIdAndItemIdAndReleasedAtIsNull(1L, 5L)).thenReturn(Optional.of(existing));

        service.setItemQuantity(1L, 5L, 7);
        assertEquals(7, it.getQuantityDispatched());
        assertEquals(7, existing.getQuantity());

        service.setItemQuantity(1L, 5L, 2);
        assertEquals(2, it.getQuantityDispatched());
        assertEquals(2, existing.getQuantity());
    }

    @Test
    void setItemQuantity_rejectsOverRequestAndNonGood() {
        lockIncident(1L, IncidentStatus.ONGOING);
        when(inventoryService.findByIdForUpdate(5L)).thenReturn(Optional.of(item(5L, 3, 2, ItemCondition.GOOD)));
        when(inventoryService.findByIdForUpdate(6L)).thenReturn(Optional.of(item(6L, 3, 0, ItemCondition.RETIRED)));
        when(itemAssignments.findByIncidentIdAndItemIdAndReleasedAtIsNull(anyLong(), anyLong())).thenReturn(Optional.empty());

        assertTrue(assertThrows(ConflictException.class, () -> service.setItemQuantity(1L, 5L, 2))
                .getMessage().contains("only 1"));
        assertTrue(assertThrows(ConflictException.class, () -> service.setItemQuantity(1L, 6L, 1))
                .getMessage().contains("RETIRED"));
    }

    @Test
    void setItemQuantity_belowOneIsBadRequest() {
        assertThrows(BadRequestException.class, () -> service.setItemQuantity(1L, 5L, 0));
        verifyNoInteractions(incidentRepository);
    }

    // ===== resolve / reopen ===== //

    @Test
    void resolve_alreadyResolvedTouchesNothing() {
        IncidentEntity i = lockIncident(1L, IncidentStatus.RESOLVED);

        assertSame(i, service.resolve(1L, null));
        verifyNoInteractions(staffService, inventoryService, staffAssignments, itemAssignments);
    }

    @Test
    void resolve_releasesInAscendingIdOrder() {
        IncidentEntity i = lockIncident(1L, IncidentStatus.ONGOING);
        StaffEntity s9 = staff(9L, StaffAvailability.DISPATCHED, true);
        StaffEntity s3 = staff(3L, StaffAvailability.DISPATCHED, true);
        ItemEntity it8 = item(8L, 5, 2, ItemCondition.GOOD);
        ItemEntity it4 = item(4L, 5, 1, ItemCondition.GOOD);
        LocalDateTime t = LocalDateTime.now();
        when(staffAssignments.findByIncidentIdAndReleasedAtIsNull(1L)).thenReturn(List.of(
                new IncidentStaffAssignment(i, s9, null, t), new IncidentStaffAssignment(i, s3, null, t)));
        when(itemAssignments.findByIncidentIdAndReleasedAtIsNull(1L)).thenReturn(List.of(
                new IncidentItemAssignment(i, it8, 2, t), new IncidentItemAssignment(i, it4, 1, t)));
        when(staffService.findByIdForUpdate(3L)).thenReturn(Optional.of(s3));
        when(staffService.findByIdForUpdate(9L)).thenReturn(Optional.of(s9));
        when(inventoryService.findByIdForUpdate(4L)).thenReturn(Optional.of(it4));
        when(inventoryService.findByIdForUpdate(8L)).thenReturn(Optional.of(it8));

        service.resolve(1L, null);

        var order = inOrder(staffService, inventoryService);
        order.verify(staffService).findByIdForUpdate(3L);
        order.verify(staffService).findByIdForUpdate(9L);
        order.verify(inventoryService).findByIdForUpdate(4L);
        order.verify(inventoryService).findByIdForUpdate(8L);
        assertEquals(IncidentStatus.RESOLVED, i.getStatus());
        assertEquals(0, it8.getQuantityDispatched());
        assertEquals(0, it4.getQuantityDispatched());
        assertEquals(StaffAvailability.AVAILABLE, s3.getStaffAvailability());
    }

    @Test
    void reopen_flipsStatusOnlyWhenResolved() {
        IncidentEntity resolved = lockIncident(1L, IncidentStatus.RESOLVED);
        resolved.setResolvedAt(LocalDateTime.now());

        service.reopen(1L);

        assertEquals(IncidentStatus.ONGOING, resolved.getStatus());
        assertNull(resolved.getResolvedAt());
        verifyNoInteractions(staffService, inventoryService);
    }

    // ===== create ===== //

    @Test
    void create_rejectsNullStaffIdAndDoesNotPersist() {
        IncidentEntity input = incident(1L, IncidentStatus.ONGOING);

        assertThrows(BadRequestException.class,
                () -> service.createIncident(input, java.util.Arrays.asList(1L, null), null));
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void deleteIncident_unknownReturnsFalseWithoutDeleting() {
        when(incidentRepository.findByIdForUpdate(7L)).thenReturn(Optional.empty());

        assertFalse(service.deleteIncident(7L));
        verify(incidentRepository, never()).delete(any());
    }
}
