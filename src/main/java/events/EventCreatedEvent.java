package com.groupnine.bumberoos.events; // <-- ITO ANG INayos

import com.groupnine.bumberoos.domain.Event;
import org.springframework.context.ApplicationEvent;

public class EventCreatedEvent extends ApplicationEvent {

    private final Event event;
    private final int importedCount;

    private EventCreatedEvent(Object source, Event event, int importedCount) {
        super(source);
        this.event = event;
        this.importedCount = importedCount;
    }

    public static EventCreatedEvent single(Object source, Event event) {
        return new EventCreatedEvent(source, event, 1);
    }

    public static EventCreatedEvent imported(Object source, int count) {
        return new EventCreatedEvent(source, null, count);
    }

    public Event getEvent() {
        return event;
    }

    public int getImportedCount() {
        return importedCount;
    }
}