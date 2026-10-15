package com.groupnine.bumberoos.domain.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One instruction the BFP Incident Commander gave the brigade at the scene. */
@Entity
@Table(name = "incident_commander_orders")
@Getter
@Setter
@NoArgsConstructor
public class IncidentCommanderOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private IncidentEntity incident;

    @Column(nullable = false, length = 1000)
    private String instruction;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    public IncidentCommanderOrder(IncidentEntity incident, String instruction, LocalDateTime issuedAt) {
        this.incident = incident;
        this.instruction = instruction;
        this.issuedAt = issuedAt;
    }
}
