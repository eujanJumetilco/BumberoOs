package com.groupnine.bumberoos.util;

import java.util.List;

/** An import that was rejected. Nothing was saved. Mapped to HTTP 400 by CsvImportExceptionHandler. */
public class CsvImportException extends RuntimeException {

    private final List<String> errors;

    public CsvImportException(String message) {
        this(List.of(message));
    }

    public CsvImportException(List<String> errors) {
        super(summary(errors));
        this.errors = List.copyOf(errors);
    }

    /** Every problem found, not just the five shown in the message. */
    public List<String> getErrors() {
        return errors;
    }

    private static String summary(List<String> errors) {
        if (errors.size() == 1 && !errors.get(0).startsWith("Row ")) return errors.get(0);
        String shown = String.join(" ", errors.subList(0, Math.min(errors.size(), 5)));
        if (errors.size() > 5) shown += " (+" + (errors.size() - 5) + " more)";
        return "Import failed, nothing saved. " + shown;
    }
}
