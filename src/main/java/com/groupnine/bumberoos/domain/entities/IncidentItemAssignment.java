package com.groupnine.bumberoos.domain.entities;

import com.groupnine.bumberoos.domain.enums.ItemCondition;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Units of an item sent to an incident. Released by setting {@code releasedAt}, never deleted. */
@Entity
@Table(name = "incident_item_assignments")
@Getter
@Setter
@NoArgsConstructor
public class IncidentItemAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private IncidentEntity incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private ItemEntity item;

    /** Units of this item currently (or, once released, previously) sent. Always >= 1. */
    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private LocalDateTime assignedAt;

    private LocalDateTime releasedAt;

    @Enumerated(EnumType.STRING)
    private ItemCondition returnCondition;

    public IncidentItemAssignment(IncidentEntity incident, ItemEntity item, int quantity, LocalDateTime assignedAt) {
        this.incident = incident;
        this.item = item;
        this.quantity = quantity;
        this.assignedAt = assignedAt;
    }

    public boolean isActive() {
        return releasedAt == null;
    }
}
