package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.dto.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the open Server-Sent Events connections (one per signed-in browser tab) and pushes each new
 * notification to all of them. Connections end after 30 minutes; the browser reconnects on its own.
 */
@Component
public class NotificationBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(NotificationBroadcaster.class);
    private static final long TIMEOUT_MS = 30L * 60 * 1000;
    private static final long KEEP_ALIVE_MS = 30L * 1000;

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(error -> emitters.remove(emitter));

        try {
            // Lets the browser know the stream is live (it reloads the list on reconnect).
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException | IllegalStateException ex) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void broadcast(NotificationResponse notification) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(notification.id()))
                        .name("notification")
                        .data(notification, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException ex) {
                emitters.remove(emitter); // that browser went away
            } catch (RuntimeException ex) {
                log.warn("Could not push notification {}: {}", notification.id(), ex.getMessage());
                emitters.remove(emitter);
            }
        }
    }

    /**
     * Sends an SSE comment to every open stream so proxies keep quiet connections open (Vercel's proxy
     * drops a request after 120 seconds without data). Browsers ignore comments.
     */
    @Scheduled(fixedRate = KEEP_ALIVE_MS)
    public void keepAlive() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | RuntimeException ex) {
                emitters.remove(emitter); // that browser went away
            }
        }
    }
}
