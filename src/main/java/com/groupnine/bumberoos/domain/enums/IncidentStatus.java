package com.groupnine.bumberoos.domain.enums;

public enum IncidentStatus {
    ONGOING,
    PENDING;

    public static IncidentStatus setStatus(int input) {
        return switch (input) {
            case 0 -> IncidentStatus.ONGOING;
            case 1 -> IncidentStatus.PENDING;
            default -> null;
        };
    }

    public static IncidentStatus setStatus(String input) {
        String status = input.toUpperCase();

        return switch (status) {
            case "ONGOING" -> IncidentStatus.ONGOING;
            case "PENDING" -> IncidentStatus.PENDING;
            default -> null;
        };
    }
}