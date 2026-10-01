package com.groupnine.bumberoos.domain.enums;

public enum ItemCondition {
    GOOD,
    DAMAGED,
    RETIRED;

    public static ItemCondition setCondition(int input){
        return switch (input) {
            case 0 -> ItemCondition.GOOD;
            case 1 -> ItemCondition.DAMAGED;
            case 2 -> ItemCondition.RETIRED;
            default -> null;
        };
    }

    public static ItemCondition setCondition(String input) {
        String condition = input.toUpperCase();

        return switch (condition) {
            case "GOOD" -> ItemCondition.GOOD;
            case "DAMAGED" -> ItemCondition.DAMAGED;
            case "RETIRED" -> ItemCondition.RETIRED;
            default -> null;
        };
    }
}
