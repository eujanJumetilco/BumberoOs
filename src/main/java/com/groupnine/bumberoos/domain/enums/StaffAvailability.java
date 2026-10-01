package com.groupnine.bumberoos.domain.enums;

public enum StaffAvailability {
    UNAVAILABLE,
    AVAILABLE,
    DISPATCHED;

    public static StaffAvailability setAvailability(int input){
        return switch (input) {
            case 0 -> StaffAvailability.UNAVAILABLE;
            case 1 -> StaffAvailability.AVAILABLE;
            case 2 -> StaffAvailability.DISPATCHED;
            default -> null;
        };
    }

    public static StaffAvailability setAvailability(String input){
        String choice = input.toUpperCase();

        return switch (choice) {
            case "UNAVAILABLE" -> StaffAvailability.UNAVAILABLE;
            case "AVAILABLE" -> StaffAvailability.AVAILABLE;
            case "DISPATCHED" -> StaffAvailability.DISPATCHED;
            default -> null;
        };
    }

}
