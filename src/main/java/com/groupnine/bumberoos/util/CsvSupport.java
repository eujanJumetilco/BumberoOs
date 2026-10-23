package com.groupnine.bumberoos.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One place for CSV (comma) and PSV (pipe) export and import, built on commons-csv.
 *
 * Export: {@link #export} writes a UTF-8 file with a BOM so Excel shows accents correctly.
 * Import: {@link #parse} auto-detects comma vs pipe, then {@link #importRows} validates EVERY row first
 * and only then saves, so a bad file never leaves half its rows behind.
 */
public final class CsvSupport {

    private CsvSupport() {}

    public record ImportResult(int imported) {}

    @FunctionalInterface
    public interface RowSaver<T> {
        void save(T value) throws Exception;
    }

    public enum Format {
        CSV(',', "csv", "text/csv; charset=UTF-8"),
        PSV('|', "psv", "text/plain; charset=UTF-8");

        private final char delimiter;
        private final String extension;
        private final String contentType;

        Format(char delimiter, String extension, String contentType) {
            this.delimiter = delimiter;
            this.extension = extension;
            this.contentType = contentType;
        }

        /** "psv" (any case) gives PSV; anything else gives CSV. */
        public static Format of(String value) {
            return value != null && value.trim().equalsIgnoreCase("psv") ? PSV : CSV;
        }
    }

    private static CSVFormat csvFormat(Format format) {
        return CSVFormat.DEFAULT.builder().setDelimiter(format.delimiter).build();
    }

    // ------------------ EXPORT ------------------ //

    public static ResponseEntity<byte[]> export(
            String baseName, Format format, List<String> headers, List<? extends List<?>> rows
    ) {
        StringWriter out = new StringWriter();
        out.write('\uFEFF');
        try (CSVPrinter printer = new CSVPrinter(out, csvFormat(format))) {
            printer.printRecord(headers);
            for (List<?> row : rows) printer.printRecord(row);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + baseName + "." + format.extension + "\"")
                .contentType(MediaType.parseMediaType(format.contentType))
                .body(out.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Builds one export row. Nulls are allowed and become empty cells. */
    public static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }

    public static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    /**
     * Stops Excel from running a cell as a formula (CSV injection): text starting with = + - @ gets a
     * leading apostrophe. Import strips it again. Use on free-text columns, never on numbers.
     */
    public static String safe(String value) {
        if (value == null || value.isEmpty()) return value;
        char c = value.charAt(0);
        return (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') ? "'" + value : value;
    }

    public static String joinList(List<String> values) {
        return values == null ? "" : safe(String.join("; ", values));
    }

    // ------------------ IMPORT ------------------ //

    public static Table parse(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new CsvImportException("The file is empty.");

        String text;
        try {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new CsvImportException("Could not read the uploaded file.");
        }
        if (text.startsWith("\uFEFF")) text = text.substring(1);

        Format format = detect(file.getOriginalFilename(), text);
        List<CSVRecord> records;
        try (var parser = csvFormat(format).parse(new StringReader(text))) {
            records = parser.getRecords();
        } catch (IOException | UncheckedIOException | IllegalArgumentException ex) {
            throw new CsvImportException("Malformed " + format.extension.toUpperCase(Locale.ROOT)
                    + " file: " + ex.getMessage());
        }
        if (records.isEmpty()) throw new CsvImportException("The file is empty.");

        Map<String, Integer> columns = new LinkedHashMap<>();
        CSVRecord header = records.get(0);
        for (int i = 0; i < header.size(); i++) columns.putIfAbsent(key(header.get(i)), i);

        List<Row> rows = new ArrayList<>();
        for (int r = 1; r < records.size(); r++) {
            CSVRecord record = records.get(r);
            boolean blank = true;
            for (String cell : record) {
                if (!cell.isBlank()) { blank = false; break; }
            }
            if (!blank) rows.add(new Row(record, columns));
        }
        return new Table(columns, rows);
    }

    /** Pipes vs commas in the header line decide; the file extension only breaks a tie. */
    private static Format detect(String filename, String text) {
        int nl = text.indexOf('\n');
        String first = nl < 0 ? text : text.substring(0, nl);
        long pipes = first.chars().filter(c -> c == '|').count();
        long commas = first.chars().filter(c -> c == ',').count();
        if (pipes > commas) return Format.PSV;
        if (commas > pipes) return Format.CSV;
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".psv") ? Format.PSV : Format.CSV;
    }

    /** "Serial Number", "serial_number" and "SERIALNUMBER" are all the same column. */
    private static String key(String header) {
        return header == null ? "" : header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String unguard(String value) {
        if (value.length() >= 2 && value.charAt(0) == '\'' && "=+-@".indexOf(value.charAt(1)) >= 0) {
            return value.substring(1);
        }
        return value;
    }

    public static final class Table {
        private final Map<String, Integer> columns;
        private final List<Row> rows;

        private Table(Map<String, Integer> columns, List<Row> rows) {
            this.columns = columns;
            this.rows = rows;
        }

        public List<Row> rows() {
            return rows;
        }

        /** Each group is one required column; "date|event date" means either name is accepted. */
        public void require(List<String> expectedHeaders, String... groups) {
            for (String group : groups) {
                String[] names = group.split("\\|");
                boolean found = Arrays.stream(names).anyMatch(n -> columns.containsKey(key(n)));
                if (!found) {
                    throw new CsvImportException("Missing required column: " + names[0]
                            + ". Expected headers: " + String.join(", ", expectedHeaders));
                }
            }
        }
    }

    public static final class Row {
        private final CSVRecord record;
        private final Map<String, Integer> columns;

        private Row(CSVRecord record, Map<String, Integer> columns) {
            this.record = record;
            this.columns = columns;
        }

        /** Row number as a spreadsheet shows it: the header is row 1, the first data row is 2. */
        public int number() {
            return (int) record.getRecordNumber();
        }

        /** First non-blank value among the given column names (aliases), trimmed; null when blank or absent. */
        public String get(String... names) {
            for (String name : names) {
                Integer i = columns.get(key(name));
                if (i == null || i >= record.size()) continue;
                String value = unguard(record.get(i).trim());
                if (!value.isEmpty()) return value;
            }
            return null;
        }
    }

    /**
     * Validate every row first, then save. Any problem at all means nothing is saved: bad rows throw before
     * the first save, and a failed save throws so the caller's @Transactional rolls the earlier saves back.
     */
    public static <T> ImportResult importRows(Table table, Function<Row, T> parser, RowSaver<T> saver) {
        List<T> parsed = new ArrayList<>();
        List<Integer> rowNumbers = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        for (Row row : table.rows()) {
            try {
                parsed.add(parser.apply(row));
                rowNumbers.add(row.number());
            } catch (ResponseStatusException ex) {
                problems.add("Row " + row.number() + ": " + ex.getReason());
            } catch (RuntimeException ex) {
                problems.add("Row " + row.number() + ": " + describe(ex));
            }
        }
        if (!problems.isEmpty()) throw new CsvImportException(problems);
        if (parsed.isEmpty()) throw new CsvImportException("The file has a header row but no data rows.");

        for (int i = 0; i < parsed.size(); i++) {
            try {
                saver.save(parsed.get(i));
            } catch (Exception ex) {
                throw new CsvImportException(List.of("Row " + rowNumbers.get(i) + ": " + describe(ex)));
            }
        }
        return new ImportResult(parsed.size());
    }

    private static String describe(Exception ex) {
        if (ex instanceof DataIntegrityViolationException) {
            return "conflicts with existing data (duplicate or invalid value).";
        }
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    // ------------------ CELL PARSERS (throw IllegalArgumentException with a readable message) ------------------ //

    public static LocalDate parseDate(String s, String column) {
        if (s == null) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(column + " '" + s + "' is invalid (use YYYY-MM-DD).");
        }
    }

    /** Accepts 2026-10-22T09:00, 2026-10-22 09:00, 2026-10-22T09:00:00Z, or just 2026-10-22 (midnight). */
    public static LocalDateTime parseDateTime(String s, String column) {
        if (s == null) return null;
        String v = s.trim().replace(' ', 'T');
        try {
            if (v.length() == 10) return LocalDate.parse(v).atStartOfDay();
            try {
                return LocalDateTime.parse(v);
            } catch (DateTimeParseException first) {
                return OffsetDateTime.parse(v).toLocalDateTime();
            }
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(column + " '" + s + "' is invalid (use YYYY-MM-DDTHH:mm).");
        }
    }

    public static Double parseDouble(String s, String column) {
        if (s == null) return null;
        try {
            double v = Double.parseDouble(s.trim());
            if (!Double.isFinite(v)) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(column + " must be a number.");
        }
    }

    public static Integer parseInt(String s, String column) {
        if (s == null) return null;
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(column + " must be a whole number.");
        }
    }

    public static boolean parseBool(String s, String column, boolean fallback) {
        if (s == null) return fallback;
        switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "y", "1" -> { return true; }
            case "false", "no", "n", "0" -> { return false; }
            default -> throw new IllegalArgumentException(column + " must be Yes or No.");
        }
    }

    /** Case-insensitive; spaces and dashes count as underscores ("Partially Dispatched"). */
    public static <E extends Enum<E>> E parseEnum(Class<E> type, String s, String column) {
        if (s == null) return null;
        String v = s.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return Enum.valueOf(type, v);
        } catch (IllegalArgumentException ex) {
            String allowed = Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
            throw new IllegalArgumentException(column + " '" + s + "' is invalid. Allowed: " + allowed + ".");
        }
    }

    /** Splits on ; or , (the UI uses commas, exports use "; "). */
    public static List<String> splitList(String s) {
        if (s == null) return List.of();
        return Arrays.stream(s.split("[;,]")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    /** Latitude and longitude come together or not at all, and must be real coordinates. */
    public static void requireCoordinates(Double lat, Double lng) {
        if ((lat == null) != (lng == null)) {
            throw new IllegalArgumentException("Latitude and longitude must be provided together.");
        }
        if (lat != null && (lat < -90 || lat > 90 || lng < -180 || lng > 180)) {
            throw new IllegalArgumentException("Latitude must be -90 to 90 and longitude -180 to 180.");
        }
    }
}