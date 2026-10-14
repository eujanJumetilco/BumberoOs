package com.groupnine.bumberoos.domain.enums;

/** Response-only, derived from condition and dispatched quantity. Never stored. */
public enum ItemAvailability {
    AVAILABLE, PARTIALLY_DISPATCHED, DISPATCHED, UNAVAILABLE
}
