package com.groupnine.bumberoos.domain.entities;

import com.groupnine.bumberoos.domain.enums.UserRole;
import com.groupnine.bumberoos.util.StringUtil;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A login account for the system. Optionally linked to the staff member who owns it. */
@Entity
@Table(name = "user_accounts")
@Getter
@Setter
@NoArgsConstructor
public class UserAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt hash, never the raw password. */
    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    private String displayName;

    private boolean enabled = true;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", unique = true)
    private StaffEntity staff;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    public UserAccountEntity(String email, String passwordHash, UserRole role, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.displayName = displayName;
        normalize();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        normalize();
    }

    @PreUpdate
    public void normalize() {
        email = StringUtil.normalizeEmail(email);
        displayName = StringUtil.toTitleCase(displayName);
    }
}
