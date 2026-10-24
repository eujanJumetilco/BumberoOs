package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.Event;
import com.groupnine.bumberoos.domain.entities.IncidentEntity;
import com.groupnine.bumberoos.domain.entities.Notification;
import com.groupnine.bumberoos.domain.enums.NotificationType;
import com.groupnine.bumberoos.dto.NotificationResponse;
import com.groupnine.bumberoos.events.EventCreatedEvent; // <-- ITO ANG IMPORT
import com.groupnine.bumberoos.events.IncidentReportedEvent; // <-- ITO ANG IMPORT
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;
    private final NotificationBroadcaster broadcaster;

    public NotificationEventListener(NotificationService notificationService, NotificationBroadcaster broadcaster) {
        this.notificationService = notificationService;
        this.broadcaster = broadcaster;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onIncidentReported(IncidentReportedEvent event) {
        publish(NotificationType.INCIDENT, incidentMessage(event.getIncident()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEventCreated(EventCreatedEvent event) {
        publish(NotificationType.EVENT, eventMessage(event));
    }

    private void publish(NotificationType type, String message) {
        try {
            Notification saved = notificationService.create(type, message);
            broadcaster.broadcast(NotificationResponse.from(saved));
        } catch (RuntimeException ex) {
            log.warn("Could not create {} notification: {}", type, ex.getMessage());
        }
    }

    private static String incidentMessage(IncidentEntity incident) {
        StringBuilder text = new StringBuilder("New incident: ");
        if (hasText(incident.getIncidentType())) text.append(incident.getIncidentType().trim());
        if (hasText(incident.getLocation())) text.append(" at ").append(incident.getLocation().trim());
        if (incident.getAlarmLevel() != null) {
            text.append(" (").append(pretty(String.valueOf(incident.getAlarmLevel()))).append(")");
        }
        return text.toString();
    }

    private static String eventMessage(EventCreatedEvent event) {
        Event saved = event.getEvent();
        if (saved == null) {
            int n = event.getImportedCount();
            return n + (n == 1 ? " event was" : " events were") + " imported";
        }
        String text = "New event added: " + saved.getTitle().trim();
        if (hasText(saved.getLocation())) text += " at " + saved.getLocation().trim();
        return text;
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }

    /** SECOND_ALARM becomes "Second Alarm". */
    private static String pretty(String enumName) {
        StringBuilder out = new StringBuilder();
        for (String word : enumName.split("_")) {
            if (out.length() > 0) out.append(' ');
            out.append(word.charAt(0)).append(word.substring(1).toLowerCase());
        }
        return out.toString();
    }
}