package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.Notification;
import com.groupnine.bumberoos.domain.enums.NotificationType;
import com.groupnine.bumberoos.repositories.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class NotificationService {

    private static final int MAX_MESSAGE_LENGTH = 500;

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Saves a notification. Always runs in its own transaction: it is called from an AFTER_COMMIT listener,
     * where the original transaction is finished and can no longer take writes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification create(NotificationType type, String message) {
        String text = message == null ? "" : message.trim();
        if (text.length() > MAX_MESSAGE_LENGTH) text = text.substring(0, MAX_MESSAGE_LENGTH - 1) + "…";
        return notificationRepository.save(new Notification(type, text));
    }

    /** The 50 most recent, newest first. */
    @Transactional(readOnly = true)
    public List<Notification> latest() {
        return notificationRepository.findTop50ByOrderByCreatedAtDescIdDesc();
    }

    /** Idempotent. Empty when the notification does not exist. */
    @Transactional
    public Optional<Notification> markRead(Long id) {
        return notificationRepository.findById(id).map(n -> {
            n.setRead(true);
            return n;
        });
    }

    /** @return how many notifications were unread. */
    @Transactional
    public int markAllRead() {
        return notificationRepository.markAllRead();
    }
}
