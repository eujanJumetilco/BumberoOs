package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.entities.Notification;
import com.groupnine.bumberoos.domain.enums.NotificationType;

import java.time.LocalDateTime;

/** What the API and the SSE stream send to the browser. */
public record NotificationResponse(
        Long id,
        String message,
        NotificationType type,
        LocalDateTime createdAt,
        boolean read
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getMessage(), n.getType(), n.getCreatedAt(), n.isRead());
    }
}
