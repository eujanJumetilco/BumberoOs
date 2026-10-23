package com.groupnine.bumberoos.events;

import com.groupnine.bumberoos.domain.entities.IncidentEntity;
import org.springframework.context.ApplicationEvent;

/**
 * Event triggered whenever a new incident is successfully reported and saved.
 */
public class IncidentReportedEvent extends ApplicationEvent {

    private final IncidentEntity incident;

    public IncidentReportedEvent(Object source, IncidentEntity incident) {
        super(source);
        this.incident = incident;
    }

    public IncidentEntity getIncident() {
        return incident;
    }
}