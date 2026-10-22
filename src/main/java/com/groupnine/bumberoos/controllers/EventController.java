package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.domain.Event;
import com.groupnine.bumberoos.repositories.EventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    // A request DTO keeps clients from setting the id directly.
    public record EventRequest(
            String title, String description, String location,
            LocalDateTime eventDate, Double latitude, Double longitude) {}

    private final EventRepository events;

    public EventController(EventRepository events) {
        this.events = events;
    }

    @GetMapping
    public List<Event> list() {
        return events.findAllByOrderByEventDateAsc();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Event create(@RequestBody EventRequest req) {
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
        return events.save(e);
    }
}