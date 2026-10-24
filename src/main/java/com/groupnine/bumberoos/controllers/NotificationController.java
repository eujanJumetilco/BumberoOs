package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.dto.NotificationResponse;
import com.groupnine.bumberoos.services.NotificationBroadcaster;
import com.groupnine.bumberoos.services.NotificationService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/** Notifications are shared by every signed-in account (see the /api/** rule in SecurityConfig). */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    public record MarkAllResult(int updated) {}

    private final NotificationService notificationService;
    private final NotificationBroadcaster broadcaster;

    public NotificationController(NotificationService notificationService, NotificationBroadcaster broadcaster) {
        this.notificationService = notificationService;
        this.broadcaster = broadcaster;
    }

    /** The 50 most recent notifications, newest first. */
    @GetMapping
    public List<NotificationResponse> list() {
        return notificationService.latest().stream().map(NotificationResponse::from).toList();
    }

    /** Live feed (Server-Sent Events). Sends a "notification" event whenever an incident or event is saved. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().noTransform())
                .header("X-Accel-Buffering", "no")
                .body(broadcaster.subscribe());
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id) {
        return notificationService.markRead(id)
                .map(NotificationResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "notification " + id + " not found"));
    }

    @PostMapping("/read-all")
    public MarkAllResult markAllRead() {
        return new MarkAllResult(notificationService.markAllRead());
    }
}
