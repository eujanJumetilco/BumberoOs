package com.groupnine.bumberoos.domain.entities;

import com.groupnine.bumberoos.util.StringUtil;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A staff member's stint on an incident. Released by setting {@code releasedAt}, never deleted. */
@Entity
@Table(name = "incident_staff_assignments")
@Getter
@Setter
@NoArgsConstructor
public class IncidentStaffAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private IncidentEntity incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private StaffEntity staff;

    /** E.g. "Driver", "Nozzleman". */
    private String role;

    @Column(nullable = false)
    private LocalDateTime assignedAt;

    private LocalDateTime releasedAt;

    public IncidentStaffAssignment(IncidentEntity incident, StaffEntity staff, String role, LocalDateTime assignedAt) {
        this.incident = incident;
        this.staff = staff;
        this.role = role;
        this.assignedAt = assignedAt;
        normalize();
    }

    public boolean isActive() {
        return releasedAt == null;
    }

    @PrePersist
    @PreUpdate
    public void normalize() {
        role = StringUtil.toTitleCase(role);
        if (role != null && role.isEmpty()) role = null;
    }
}
