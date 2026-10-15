package com.groupnine.bumberoos;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtcTimeZoneTest {

    @Test
    void applicationPinsTheJvmToUtc() throws Exception {
        // Loading the class runs its static initializer.
        Class.forName(BumberoosApplication.class.getName());
        assertEquals("UTC", TimeZone.getDefault().getID());

        long drift = Math.abs(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - System.currentTimeMillis() / 1000);
        assertTrue(drift < 5, "LocalDateTime.now() should read as UTC");
    }
}
