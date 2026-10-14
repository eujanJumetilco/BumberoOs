package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.IncidentEntity;
import com.groupnine.bumberoos.domain.entities.ItemEntity;
import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.domain.enums.*;
import com.groupnine.bumberoos.domain.exceptions.ConflictException;
import com.groupnine.bumberoos.domain.exceptions.ResourceNotFoundException;
import com.groupnine.bumberoos.repositories.*;
import com.groupnine.bumberoos.services.DispatchService.ItemRequestLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Not @Transactional on purpose: these tests need real commits and rollbacks to verify that creation
 * is all-or-nothing and that concurrent dispatchers cannot double-book. Cleans up after itself.
 */
@SpringBootTest
class DispatchServiceTransactionTest {

    @Autowired DispatchService dispatch;
    @Autowired StaffService staffService;
    @Autowired InventoryService inventoryService;
    @Autowired IncidentRepository incidentRepository;
    @Autowired StaffRepository staffRepository;
    @Autowired ItemRepository itemRepository;

    @AfterEach
    void cleanUp() {
        incidentRepository.deleteAll(); // cascades to assignments and alarm events
        staffRepository.deleteAll();
        itemRepository.deleteAll();
    }

    private StaffEntity staff(String name, StaffAvailability availability) {
        StaffEntity s = new StaffEntity(name, "volunteer", null, null, true, new ArrayList<>(), new ArrayList<>());
        s.setStaffAvailability(availability);
        return staffService.addStaff(s);
    }

    private ItemEntity item(String name, int quantity) throws Exception {
        return inventoryService.addItem(new ItemEntity(ItemCondition.GOOD, name, null, quantity));
    }

    private IncidentEntity newIncident() {
        return new IncidentEntity(IncidentNature.EMERGENCY, "fire", "zone 3", LocalDateTime.now(), "juan");
    }

    @Test
    void create_failsAtomically_whenOneStaffIsAlreadyDispatched() throws Exception {
        StaffEntity free = staff("free ana", StaffAvailability.AVAILABLE);
        StaffEntity busy = staff("busy ben", StaffAvailability.DISPATCHED);
        ItemEntity suits = item("fire suit", 10);

        assertThrows(ConflictException.class, () -> dispatch.createIncident(newIncident(),
                List.of(free.getId(), busy.getId()), List.of(new ItemRequestLine(suits.getId(), 3))));

        assertEquals(0, incidentRepository.count(), "no incident row");
        assertEquals(StaffAvailability.AVAILABLE, staffRepository.findById(free.getId()).orElseThrow().getStaffAvailability());
        assertEquals(0, itemRepository.findById(suits.getId()).orElseThrow().getQuantityDispatched());
    }

    @Test
    void create_failsAtomically_withUnknownStaffOrItemId() throws Exception {
        StaffEntity free = staff("free ana", StaffAvailability.AVAILABLE);
        ItemEntity suits = item("fire suit", 10);

        assertThrows(ResourceNotFoundException.class, () -> dispatch.createIncident(newIncident(),
                List.of(free.getId(), -1L), null));
        assertThrows(ResourceNotFoundException.class, () -> dispatch.createIncident(newIncident(),
                List.of(free.getId()), List.of(new ItemRequestLine(suits.getId(), 2), new ItemRequestLine(-1L, 1))));

        assertEquals(0, incidentRepository.count());
        assertEquals(StaffAvailability.AVAILABLE, staffRepository.findById(free.getId()).orElseThrow().getStaffAvailability());
        assertEquals(0, itemRepository.findById(suits.getId()).orElseThrow().getQuantityDispatched());
    }

    @Test
    void create_failsAtomically_whenItemQuantityExceedsStock() throws Exception {
        StaffEntity free = staff("free ana", StaffAvailability.AVAILABLE);
        ItemEntity suits = item("fire suit", 2);

        assertThrows(ConflictException.class, () -> dispatch.createIncident(newIncident(),
                List.of(free.getId()), List.of(new ItemRequestLine(suits.getId(), 3))));

        assertEquals(0, incidentRepository.count());
        assertEquals(StaffAvailability.AVAILABLE, staffRepository.findById(free.getId()).orElseThrow().getStaffAvailability());
    }

    @Test
    void concurrentDispatchOfTheSameStaffMember_exactlyOneWins() throws Exception {
        StaffEntity ana = staff("ana", StaffAvailability.AVAILABLE);
        Long incidentA = dispatch.createIncident(newIncident(), null, null).getId();
        Long incidentB = dispatch.createIncident(newIncident(), null, null).getId();

        Results r = race(
                () -> dispatch.assignStaff(incidentA, ana.getId(), null),
                () -> dispatch.assignStaff(incidentB, ana.getId(), null));

        assertEquals(1, r.successes.get());
        assertEquals(1, r.conflicts.get());
        assertEquals(0, r.unexpected.get());
        assertEquals(StaffAvailability.DISPATCHED, staffRepository.findById(ana.getId()).orElseThrow().getStaffAvailability());
    }

    @Test
    void concurrentDispatchOfTheLastUnit_exactlyOneWins() throws Exception {
        ItemEntity engine = item("engine 1", 1);
        Long incidentA = dispatch.createIncident(newIncident(), null, null).getId();
        Long incidentB = dispatch.createIncident(newIncident(), null, null).getId();

        Results r = race(
                () -> dispatch.setItemQuantity(incidentA, engine.getId(), 1),
                () -> dispatch.setItemQuantity(incidentB, engine.getId(), 1));

        assertEquals(1, r.successes.get());
        assertEquals(1, r.conflicts.get());
        assertEquals(0, r.unexpected.get());
        assertEquals(1, itemRepository.findById(engine.getId()).orElseThrow().getQuantityDispatched());
    }

    // ===== race helper ===== //

    private static class Results {
        final AtomicInteger successes = new AtomicInteger();
        final AtomicInteger conflicts = new AtomicInteger();
        final AtomicInteger unexpected = new AtomicInteger();
    }

    /** Runs both tasks on separate threads released together by a latch. */
    private Results race(Runnable first, Runnable second) throws Exception {
        Results results = new Results();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable task : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        task.run();
                        results.successes.incrementAndGet();
                    } catch (ConflictException e) {
                        results.conflicts.incrementAndGet();
                    } catch (Exception e) {
                        results.unexpected.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        return results;
    }
}
