package com.groupnine.bumberoos.domain.entities;

import com.groupnine.bumberoos.domain.enums.AlarmLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One entry in an incident's alarm-level history. */
@Entity
@Table(name = "incident_alarm_events")
@Getter
@Setter
@NoArgsConstructor
public class IncidentAlarmEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private IncidentEntity incident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlarmLevel alarmLevel;

    @Column(nullable = false)
    private LocalDateTime raisedAt;

    public IncidentAlarmEvent(IncidentEntity incident, AlarmLevel alarmLevel, LocalDateTime raisedAt) {
        this.incident = incident;
        this.alarmLevel = alarmLevel;
        this.raisedAt = raisedAt;
    }
}
