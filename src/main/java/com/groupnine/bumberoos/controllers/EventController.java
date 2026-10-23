package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.domain.Event;
import com.groupnine.bumberoos.repositories.EventRepository;
import com.groupnine.bumberoos.util.CsvSupport;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    // A request DTO keeps clients from setting the id directly.
    public record EventRequest(
            String title, String description, String location,
            LocalDateTime eventDate, Double latitude, Double longitude) {}

    /** Export column order. ID is informational: import creates NEW events and ignores it. */
    private static final List<String> CSV_HEADERS =
            List.of("ID", "Title", "Description", "Location", "Date", "Lat", "Lng");

    private final EventRepository events;

    public EventController(EventRepository events) {
        this.events = events;
    }

    // ------------------ CRUD ------------------ //

    @GetMapping
    public List<Event> list() {
        return events.findAllByOrderByEventDateAsc();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Event create(@RequestBody EventRequest req) {
        return events.save(buildEvent(req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) {
        if (!events.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "event " + id + " not found");
        }
        events.deleteById(id);
    }

    // ------------------ CSV / PSV EXPORT & IMPORT ------------------ //

    /** GET /api/events/export?format=csv|psv. Date is an ISO local date-time (2026-10-22T09:00). */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportFile(@RequestParam(defaultValue = "csv") String format) {
        List<List<Object>> rows = new ArrayList<>();
        for (Event e : events.findAllByOrderByEventDateAsc()) {
            rows.add(CsvSupport.row(
                    e.getId(), CsvSupport.safe(e.getTitle()), CsvSupport.safe(e.getDescription()),
                    CsvSupport.safe(e.getLocation()), e.getEventDate(), e.getLatitude(), e.getLongitude()));
        }
        return CsvSupport.export("events", CsvSupport.Format.of(format), CSV_HEADERS, rows);
    }

    /**
     * POST /api/events/import (multipart field "file", CSV or PSV). Every row becomes a NEW event (the ID
     * column is ignored so clients can't set ids). Required: Title, Location, Date.
     * All-or-nothing: one bad row rejects the whole file.
     */
    @PostMapping("/import")
    @Transactional(rollbackFor = Exception.class)
    public CsvSupport.ImportResult importFile(@RequestParam("file") MultipartFile file) {
        CsvSupport.Table table = CsvSupport.parse(file);
        table.require(CSV_HEADERS, "title", "location", "date|event date");
        return CsvSupport.importRows(table, this::toEvent, events::save);
    }

    // ------------------ HELPERS ------------------ //

    private Event toEvent(CsvSupport.Row row) {
        Double lat = CsvSupport.parseDouble(row.get("lat", "latitude"), "Lat");
        Double lng = CsvSupport.parseDouble(row.get("lng", "longitude", "lon"), "Lng");
        CsvSupport.requireCoordinates(lat, lng);
        return buildEvent(new EventRequest(
                row.get("title"),
                row.get("description"),
                row.get("location"),
                CsvSupport.parseDateTime(row.get("date", "event date"), "Date"),
                lat, lng));
    }

    /** Shared by POST and import so both enforce the same rules. */
    private Event buildEvent(EventRequest req) {
        if (req.title() == null || req.title().isBlank()
                || req.location() == null || req.location().isBlank()
                || req.eventDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Title, location and event date are required.");
        }
        if ((req.latitude() == null) != (req.longitude() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Latitude and longitude must be provided together.");
        }

        Event e = new Event();
        e.setTitle(req.title().trim());
        e.setDescription(req.description() == null || req.description().isBlank()
                ? null : req.description().trim());
        e.setLocation(req.location().trim());
        e.setEventDate(req.eventDate());
        e.setLatitude(req.latitude());
        e.setLongitude(req.longitude());
        return e;
    }
}
