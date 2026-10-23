package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.*;
import com.groupnine.bumberoos.domain.enums.IncidentNature;
import com.groupnine.bumberoos.domain.enums.IncidentStatus;
import com.groupnine.bumberoos.domain.enums.ItemCondition;
import com.groupnine.bumberoos.domain.enums.StaffAvailability;
import com.groupnine.bumberoos.domain.exceptions.BadRequestException;
import com.groupnine.bumberoos.domain.exceptions.ConflictException;
import com.groupnine.bumberoos.domain.exceptions.ResourceNotFoundException;
import com.groupnine.bumberoos.events.IncidentReportedEvent;
import com.groupnine.bumberoos.repositories.IncidentItemAssignmentRepository;
import com.groupnine.bumberoos.repositories.IncidentRepository;
import com.groupnine.bumberoos.repositories.IncidentStaffAssignmentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Orchestrates assigning/releasing staff and items, resolving, reopening and deleting incidents,
 * keeping {@code StaffAvailability} and {@code quantityDispatched} in sync.
 *
 * <p>Every method locks the incident first, then staff, then items, each in ascending id order, to
 * avoid deadlocks between concurrent dispatchers. This is the only code allowed to change
 * {@code quantityDispatched}. Releasing sets {@code releasedAt}; rows are never removed from the
 * incident's assignment collections (that would delete them, via orphanRemoval).
 *
 * <p>Unlike the other services this one throws unchecked domain exceptions, since one call can fail
 * for several different reasons.
 */
@Service
@Transactional
public class DispatchService {

    public record ItemRequestLine(Long itemId, int quantity) {}

    public record ItemReturnLine(Long itemId, ItemCondition condition) {}

    private final IncidentRepository incidentRepository;
    private final IncidentStaffAssignmentRepository staffAssignmentRepository;
    private final IncidentItemAssignmentRepository itemAssignmentRepository;
    private final StaffService staffService;
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;

    public DispatchService(
            IncidentRepository incidentRepository,
            IncidentStaffAssignmentRepository staffAssignmentRepository,
            IncidentItemAssignmentRepository itemAssignmentRepository,
            StaffService staffService,
            InventoryService inventoryService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.incidentRepository = incidentRepository;
        this.staffAssignmentRepository = staffAssignmentRepository;
        this.itemAssignmentRepository = itemAssignmentRepository;
        this.staffService = staffService;
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
    }

    // =====> CREATE <===== //

    /**
     * Creates the incident and assigns everything requested, all-or-nothing: any failure throws and
     * rolls back the whole transaction. Duplicate staff ids are collapsed; duplicate item lines are
     * merged by summing their quantities.
     */
    public IncidentEntity createIncident(
            IncidentEntity incident,
            Collection<Long> staffIds,
            Collection<ItemRequestLine> items
    ) {
        if (incident.getAlarmLevel() != null && incident.getNature() != IncidentNature.EMERGENCY) {
            throw new BadRequestException("alarmLevel can only be set on an EMERGENCY incident");
        }

        if ((incident.getLatitude() == null) != (incident.getLongitude() == null)) {
            throw new BadRequestException("latitude and longitude must be given together");
        }

        TreeSet<Long> staffToAssign = new TreeSet<>();
        if (staffIds != null) {
            for (Long staffId : staffIds) {
                if (staffId == null) throw new BadRequestException("staffIds must not contain null");
                staffToAssign.add(staffId);
            }
        }
        TreeMap<Long, Integer> itemsToAssign = new TreeMap<>();
        if (items != null) {
            for (ItemRequestLine line : items) {
                if (line == null || line.itemId() == null) throw new BadRequestException("items must have an itemId");
                if (line.quantity() < 1) throw new BadRequestException("item quantity must be at least 1");
                itemsToAssign.merge(line.itemId(), line.quantity(), Integer::sum);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        incident.setId(null);
        incident.setStatus(IncidentStatus.ONGOING);
        if (incident.getDateTimeReported() == null) incident.setDateTimeReported(now);
        if (incident.getAlarmLevel() != null) {
            incident.getAlarmEvents().add(new IncidentAlarmEvent(incident, incident.getAlarmLevel(), now));
        }
        IncidentEntity saved = incidentRepository.save(incident);

        for (Long staffId : staffToAssign) assignStaffTo(saved, staffId, null, now);
        for (Map.Entry<Long, Integer> line : itemsToAssign.entrySet()) {
            setItemQuantityOn(saved, line.getKey(), line.getValue(), now);
        }

        // BAGONG LINYA: I-trigger ang event dito
        eventPublisher.publishEvent(new IncidentReportedEvent(this, saved));

        return saved;
    }

    // =====> STAFF <===== //

    /** Idempotent: re-assigning the same staff member only updates the role (when one is given). */
    public IncidentEntity assignStaff(Long incidentId, Long staffId, String role) {
        IncidentEntity incident = lockOngoingIncident(incidentId);
        assignStaffTo(incident, staffId, role, LocalDateTime.now());
        return incident;
    }

    /** No-op when the staff member has no active assignment on this incident. */
    public IncidentEntity releaseStaff(Long incidentId, Long staffId) {
        IncidentEntity incident = lockIncident(incidentId);
        staffAssignmentRepository.findByIncidentIdAndStaffIdAndReleasedAtIsNull(incidentId, staffId)
                .ifPresent(assignment -> {
                    StaffEntity staff = staffService.findByIdForUpdate(staffId).orElseThrow(() -> staffNotFound(staffId));
                    assignment.setReleasedAt(LocalDateTime.now());
                    staff.setStaffAvailability(StaffAvailability.AVAILABLE);
                });
        return incident;
    }

    // =====> ITEMS <===== //

    /** PUT semantics: {@code quantity} is the total units of this item that should be on the incident. */
    public IncidentEntity setItemQuantity(Long incidentId, Long itemId, int quantity) {
        if (quantity < 1) throw new BadRequestException("quantity must be at least 1");
        IncidentEntity incident = lockOngoingIncident(incidentId);
        setItemQuantityOn(incident, itemId, quantity, LocalDateTime.now());
        return incident;
    }

    /** Returns all units of the item. No-op when it has no active assignment on this incident. */
    public IncidentEntity releaseItem(Long incidentId, Long itemId) {
        IncidentEntity incident = lockIncident(incidentId);
        itemAssignmentRepository.findByIncidentIdAndItemIdAndReleasedAtIsNull(incidentId, itemId)
                .ifPresent(assignment -> {
                    ItemEntity item = inventoryService.findByIdForUpdate(itemId).orElseThrow(() -> itemNotFound(itemId));
                    returnUnits(item, assignment.getQuantity());
                    assignment.setReleasedAt(LocalDateTime.now());
                });
        return incident;
    }

    // =====> RESOLVE / REOPEN / DELETE <===== //

    /**
     * Releases every active assignment, returns staff to AVAILABLE and units to the pool, then marks the
     * incident RESOLVED. An item's return condition applies to the whole item row. Idempotent.
     */
    public IncidentEntity resolve(Long incidentId, List<ItemReturnLine> itemReturns) {
        IncidentEntity incident = lockIncident(incidentId);
        if (incident.getStatus() == IncidentStatus.RESOLVED) return incident;

        Map<Long, ItemCondition> returns = new HashMap<>();
        if (itemReturns != null) {
            for (ItemReturnLine r : itemReturns) {
                if (r == null || r.itemId() == null || r.condition() == null) {
                    throw new BadRequestException("each item return needs an itemId and a condition");
                }
                returns.put(r.itemId(), r.condition());
            }
        }

        releaseAllActive(incident, returns);
        LocalDateTime now = LocalDateTime.now();
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(now);
        return incident;
    }

    /** Does not re-dispatch anyone. No-op when already ONGOING. */
    public IncidentEntity reopen(Long incidentId) {
        IncidentEntity incident = lockIncident(incidentId);
        if (incident.getStatus() == IncidentStatus.ONGOING) return incident;
        incident.setStatus(IncidentStatus.ONGOING);
        incident.setResolvedAt(null);
        return incident;
    }

    /** Releases anything still dispatched (if ONGOING) before deleting. @return false if not found. */
    public boolean deleteIncident(Long incidentId) {
        Optional<IncidentEntity> found = incidentRepository.findByIdForUpdate(incidentId);
        if (found.isEmpty()) return false;
        IncidentEntity incident = found.get();
        if (incident.getStatus() == IncidentStatus.ONGOING) releaseAllActive(incident, Map.of());
        incidentRepository.delete(incident);
        return true;
    }

    // =====> INTERNALS <===== //

    private IncidentEntity lockIncident(Long incidentId) {
        return incidentRepository.findByIdForUpdate(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("incident " + incidentId + " not found"));
    }

    private IncidentEntity lockOngoingIncident(Long incidentId) {
        IncidentEntity incident = lockIncident(incidentId);
        if (incident.getStatus() != IncidentStatus.ONGOING) {
            throw new ConflictException("incident " + incidentId + " is " + incident.getStatus());
        }
        return incident;
    }

    private void assignStaffTo(IncidentEntity incident, Long staffId, String role, LocalDateTime now) {
        StaffEntity staff = staffService.findByIdForUpdate(staffId).orElseThrow(() -> staffNotFound(staffId));

        Optional<IncidentStaffAssignment> existing = staffAssignmentRepository
                .findByIncidentIdAndStaffIdAndReleasedAtIsNull(incident.getId(), staffId);
        if (existing.isPresent()) {
            if (role != null && !role.isBlank()) {
                existing.get().setRole(role);
                existing.get().normalize();
            }
            return;
        }

        if (!staff.isEmployed()) throw new ConflictException(staff.getName() + " is not employed");
        if (staff.getStaffAvailability() != StaffAvailability.AVAILABLE) {
            throw new ConflictException(staff.getName() + " is " + staff.getStaffAvailability());
        }

        IncidentStaffAssignment assignment = new IncidentStaffAssignment(incident, staff, role, now);
        incident.getStaffAssignments().add(assignment);
        staffAssignmentRepository.save(assignment);
        staff.setStaffAvailability(StaffAvailability.DISPATCHED);
    }

    private void setItemQuantityOn(IncidentEntity incident, Long itemId, int quantity, LocalDateTime now) {
        ItemEntity item = inventoryService.findByIdForUpdate(itemId).orElseThrow(() -> itemNotFound(itemId));

        Optional<IncidentItemAssignment> existing = itemAssignmentRepository
                .findByIncidentIdAndItemIdAndReleasedAtIsNull(incident.getId(), itemId);
        int current = existing.map(IncidentItemAssignment::getQuantity).orElse(0);
        int delta = quantity - current;
        if (delta == 0) return;

        if (delta > 0) {
            requireUsable(item, delta);
            item.setQuantityDispatched(item.getQuantityDispatched() + delta);
        } else {
            returnUnits(item, -delta);
        }

        if (existing.isPresent()) {
            existing.get().setQuantity(quantity);
        } else {
            IncidentItemAssignment assignment = new IncidentItemAssignment(incident, item, quantity, now);
            incident.getItemAssignments().add(assignment);
            itemAssignmentRepository.save(assignment);
        }
        assertQuantityInvariant(item);
    }

    private void requireUsable(ItemEntity item, int units) {
        if (item.getItemCondition() != ItemCondition.GOOD) {
            throw new ConflictException(item.getName() + " is " + item.getItemCondition());
        }
        if (item.getQuantityAvailable() < units) {
            throw new ConflictException("only " + item.getQuantityAvailable() + " of " + item.getName()
                    + " available, " + units + " requested");
        }
    }

    private void returnUnits(ItemEntity item, int units) {
        item.setQuantityDispatched(item.getQuantityDispatched() - units);
        assertQuantityInvariant(item);
    }

    private void assertQuantityInvariant(ItemEntity item) {
        if (item.getQuantityDispatched() < 0 || item.getQuantityDispatched() > item.getQuantity()) {
            throw new IllegalStateException("dispatched quantity of " + item.getName() + " out of range: "
                    + item.getQuantityDispatched() + " of " + item.getQuantity());
        }
    }

    /** Shared by resolve and delete. Locks staff then items, each in ascending id order. */
    private void releaseAllActive(IncidentEntity incident, Map<Long, ItemCondition> itemReturns) {
        LocalDateTime now = LocalDateTime.now();

        List<IncidentStaffAssignment> staffAssignments = new ArrayList<>(
                staffAssignmentRepository.findByIncidentIdAndReleasedAtIsNull(incident.getId()));
        List<IncidentItemAssignment> itemAssignments = new ArrayList<>(
                itemAssignmentRepository.findByIncidentIdAndReleasedAtIsNull(incident.getId()));

        Set<Long> onIncident = new HashSet<>();
        itemAssignments.forEach(a -> onIncident.add(a.getItem().getId()));
        for (Long returnedItemId : itemReturns.keySet()) {
            if (!onIncident.contains(returnedItemId)) {
                throw new BadRequestException("item " + returnedItemId + " is not on incident " + incident.getId());
            }
        }

        staffAssignments.sort(Comparator.comparing(a -> a.getStaff().getId()));
        for (IncidentStaffAssignment assignment : staffAssignments) {
            StaffEntity staff = staffService.findByIdForUpdate(assignment.getStaff().getId())
                    .orElseThrow(() -> staffNotFound(assignment.getStaff().getId()));
            assignment.setReleasedAt(now);
            staff.setStaffAvailability(StaffAvailability.AVAILABLE);
        }

        itemAssignments.sort(Comparator.comparing(a -> a.getItem().getId()));
        for (IncidentItemAssignment assignment : itemAssignments) {
            Long itemId = assignment.getItem().getId();
            ItemEntity item = inventoryService.findByIdForUpdate(itemId).orElseThrow(() -> itemNotFound(itemId));
            returnUnits(item, assignment.getQuantity());
            assignment.setReleasedAt(now);
            ItemCondition condition = itemReturns.get(itemId);
            if (condition != null) {
                assignment.setReturnCondition(condition);
                item.setItemCondition(condition);
            }
        }
    }

    private ResourceNotFoundException staffNotFound(Long id) {
        return new ResourceNotFoundException("staff " + id + " not found");
    }

    private ResourceNotFoundException itemNotFound(Long id) {
        return new ResourceNotFoundException("item " + id + " not found");
    }
}