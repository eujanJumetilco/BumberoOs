package com.groupnine.bumberoos.util;

import java.util.ArrayList;

public final class StringUtil {

    private StringUtil() {
    }

    public static String clean(String input) {
        if (input == null) {
            return null;
        }
        return input.trim().replaceAll("\\s+", " ");
    }

    public static String toTitleCase(String input) {
        String cleaned = clean(input);
        if (cleaned == null || cleaned.isEmpty()) {
            return cleaned;
        }

        String[] words = cleaned.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1).toLowerCase());
            }
            result.append(" ");
        }

        return result.toString().trim();
    }

    public static String capitalizeFirstLetter(String input) {
        String cleaned = clean(input);
        if (cleaned == null || cleaned.isEmpty()) {
            return cleaned;
        }
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    public static String normalizeEmail(String input) {
        String cleaned = clean(input);
        return cleaned == null ? null : cleaned.toLowerCase();
    }

    public static String normalizePhoneNumber(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        boolean hasLeadingPlus = trimmed.startsWith("+");
        String digitsOnly = trimmed.replaceAll("[^0-9]", "");

        if (digitsOnly.isEmpty()) {
            return clean(input);
        }

        return hasLeadingPlus ? "+" + digitsOnly : digitsOnly;
    }

    public static ArrayList<String> cleanList(ArrayList<String> input) {
        ArrayList<String> result = new ArrayList<>();
        if (input == null) {
            return result;
        }

        for (String entry : input) {
            String cleaned = toTitleCase(entry);
            if (cleaned != null && !cleaned.isEmpty()) {
                result.add(cleaned);
            }
        }

        return result;
    }

    public static ArrayList<String> cleanListFromCsv(String rawCsv) {
        if (rawCsv == null || rawCsv.isBlank()) {
            return new ArrayList<>();
        }
        return cleanList(new ArrayList<>(java.util.Arrays.asList(rawCsv.split(","))));
    }

    public static boolean containsLetter(String input) {
        if (input == null) {
            return false;
        }
        for (int i = 0; i < input.length(); i++) {
            if (Character.isLetter(input.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}