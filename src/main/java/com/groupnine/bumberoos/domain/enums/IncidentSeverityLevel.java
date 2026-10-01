package com.groupnine.bumberoos.domain.enums;

public enum IncidentSeverityLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static IncidentSeverityLevel setSeverity(int input) {
        return switch (input) {
            case 0 -> IncidentSeverityLevel.LOW;
            case 1 -> IncidentSeverityLevel.MEDIUM;
            case 2 -> IncidentSeverityLevel.HIGH;
            case 3 -> IncidentSeverityLevel.CRITICAL;
            default -> null;
        };
    }

    public static IncidentSeverityLevel setSeverity(String input) {
        String severity = input.toUpperCase();

        return switch (severity) {
            case "LOW" -> IncidentSeverityLevel.LOW;
            case "MEDIUM" -> IncidentSeverityLevel.MEDIUM;
            case "HIGH" -> IncidentSeverityLevel.HIGH;
            case "CRITICAL" -> IncidentSeverityLevel.CRITICAL;
            default -> null;
        };
    }
}