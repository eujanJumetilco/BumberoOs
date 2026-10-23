package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.events.IncidentReportedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Listens for specific events and handles the business logic that should happen afterwards.
 * This keeps IncidentService decoupled from side-effects like notifications or alerts.
 */
@Service
public class IncidentEventListener {

    @EventListener
    public void handleIncidentReported(IncidentReportedEvent event) {
        var incident = event.getIncident();

        // Ito yung "Handler" part. Dito mo ilalagay yung mga gusto mong mangyari.
        System.out.println("🔥 [EVENT] A new incident has been reported!");
        System.out.println("   -> Nature: " + incident.getNature());
        System.out.println("   -> Location: " + incident.getLocation());
        System.out.println("   -> Reported By: " + incident.getReportedBy());

        // TODO: Add logic here (e.g., send notification, create alert, log to file)
    }
}