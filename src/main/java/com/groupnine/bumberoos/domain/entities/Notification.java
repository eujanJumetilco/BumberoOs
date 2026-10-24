package com.groupnine.bumberoos.domain.entities;

import com.groupnine.bumberoos.domain.enums.NotificationType;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * One notification, shared by every account. {@code read} is a single flag for the whole station:
 * once anyone marks it read, it is read for everyone. createdAt is stored in UTC like the rest of the app.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    public Notification() {}

    public Notification(NotificationType type, String message) {
        this.type = type;
        this.message = message;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}
