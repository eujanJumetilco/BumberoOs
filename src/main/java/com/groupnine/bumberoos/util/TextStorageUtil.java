package com.groupnine.bumberoos.util;

import com.groupnine.bumberoos.domain.entities.*;
import com.groupnine.bumberoos.domain.enums.*;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TextStorageUtil {

    // ==============================
    // DIRECTORY SAFETY
    // ==============================

    private static void ensureParentDirExists(String filename) {
        File file = new File(filename);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    // ==============================
    // STAFF
    // ==============================

    public static void saveStaffToTextFile(
            List<StaffEntity> staffList, String filename) {

        ensureParentDirExists(filename);

        try (BufferedWriter writer =
                     new BufferedWriter(new FileWriter(filename))) {

            for (StaffEntity staff : staffList) {

                writer.write(
                        staff.getId() + "|" +
                        staff.getName() + "|" +
                        staff.getRole() + "|" +
                        staff.getContactNumber() + "|" +
                        staff.getAddress() + "|" +
                        staff.isEmployed() + "|" +
                        staff.getStaffAvailabilityAsString() + "|" +
                        String.join(",", staff.getTrainings()) + "|" +
                        String.join(",", staff.getCertifications()) + "|" +
                        staff.isAdmin() + "|" +
                        safeString(staff.getEmail()) + "|" +
                        safeString(staff.getPassword())
                );

                writer.newLine();
            }

        } catch (IOException e) {
            System.out.println(
                    "Error saving staff records: " + e.getMessage()
            );
        }
    }

    public static ArrayList<StaffEntity> loadStaffFromTextFile(
            String filename) {

        ArrayList<StaffEntity> staffList = new ArrayList<>();

        File file = new File(filename);

        if (!file.exists()) {
            return staffList;
        }

        try (BufferedReader reader =
                     new BufferedReader(new FileReader(filename))) {

            String line;

            while ((line = reader.readLine()) != null) {

                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] fields = line.split("\\|", -1);

                if (fields.length < 12) {
                    System.out.println(
                            "Skipping invalid staff record."
                    );
                    continue;
                }

                // Each record is parsed independently so that one malformed
                // line (e.g. a corrupted ID) does not stop the remaining,
                // valid staff records from being loaded.
                try {
                    int id = Integer.parseInt(fields[0]);
                    String name = fields[1];
                    String role = fields[2];
                    String contactNumber = fields[3];
                    String address = fields[4];
                    boolean employed = Boolean.parseBoolean(fields[5]);

                    StaffAvailability availability =
                            StaffAvailability.setAvailability(fields[6]);

                    ArrayList<String> trainings =
                            toArrayList(fields[7]);

                    ArrayList<String> certifications =
                            toArrayList(fields[8]);

                    boolean isAdmin =
                            Boolean.parseBoolean(fields[9]);

                    String email = emptyToNull(fields[10]);
                    String password = emptyToNull(fields[11]);

                    StaffEntity staff = new StaffEntity(
                            isAdmin,
                            name,
                            role,
                            contactNumber,
                            address,
                            employed,
                            trainings,
                            certifications
                    );

                    staff.setId(id);

                    if (availability != null) {
                        staff.setStaffAvailability(
                                availability.ordinal()
                        );
                    }

                    if (isAdmin && email != null && password != null) {
                        staff.setAdminCredentials(email, password);
                    }

                    staffList.add(staff);

                } catch (NumberFormatException e) {
                    System.out.println(
                            "Skipping staff record with invalid number: " + line
                    );
                }
            }

        } catch (IOException e) {
            System.out.println(
                    "Error loading staff records: " + e.getMessage()
            );
        }

        return staffList;
    }


    // ==============================
    // INCIDENT
    // ==============================

    public static void saveIncidentsToTextFile(
            List<IncidentEntity> incidents, String filename) {

        ensureParentDirExists(filename);

        try (BufferedWriter writer =
                     new BufferedWriter(new FileWriter(filename))) {

            for (IncidentEntity incident : incidents) {

                writer.write(
                        incident.getId() + "|" +
                        incident.getIncidentType() + "|" +
                        incident.getLocation() + "|" +
                        incident.getDateTimeReported() + "|" +
                        incident.getReportedBy() + "|" +
                        incident.getSeverityLevel().name() + "|" +
                        incident.getStatus().name()
                );

                writer.newLine();
            }

        } catch (IOException e) {
            System.out.println(
                    "Error saving incident records: " + e.getMessage()
            );
        }
    }

    public static ArrayList<IncidentEntity> loadIncidentsFromTextFile(
            String filename) {

        ArrayList<IncidentEntity> incidents = new ArrayList<>();

        File file = new File(filename);

        if (!file.exists()) {
            return incidents;
        }

        try (BufferedReader reader =
                     new BufferedReader(new FileReader(filename))) {

            String line;

            while ((line = reader.readLine()) != null) {

                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] fields = line.split("\\|", -1);

                if (fields.length < 7) {
                    System.out.println(
                            "Skipping invalid incident record."
                    );
                    continue;
                }

                // Parse each record on its own so a single bad ID/date does
                // not discard the incident records that come after it.
                try {
                    int id = Integer.parseInt(fields[0]);
                    String type = fields[1];
                    String location = fields[2];

                    LocalDateTime dateTime =
                            LocalDateTime.parse(fields[3]);

                    String reportedBy = fields[4];

                    IncidentSeverityLevel severity =
                            IncidentSeverityLevel.setSeverity(fields[5]);

                    IncidentStatus status =
                            IncidentStatus.setStatus(fields[6]);

                    if (severity == null || status == null) {
                        System.out.println(
                                "Skipping incident with invalid enum data."
                        );
                        continue;
                    }

                    IncidentEntity incident = new IncidentEntity(
                            id,
                            severity.ordinal(),
                            status.ordinal(),
                            type,
                            location,
                            dateTime,
                            reportedBy
                    );

                    incidents.add(incident);

                } catch (NumberFormatException e) {
                    System.out.println(
                            "Skipping incident with invalid number: " + line
                    );
                } catch (java.time.format.DateTimeParseException e) {
                    System.out.println(
                            "Skipping incident with invalid date: " + line
                    );
                }
            }

        } catch (IOException e) {
            System.out.println(
                    "Error loading incident records: " + e.getMessage()
            );
        }

        return incidents;
    }


    // ==============================
    // INVENTORY
    // ==============================

    public static void saveInventoryToTextFile(
            List<ItemEntity> inventory, String filename) {

        ensureParentDirExists(filename);

        try (BufferedWriter writer =
                     new BufferedWriter(new FileWriter(filename))) {

            for (ItemEntity item : inventory) {

                writer.write(
                        item.getId() + "|" +
                        item.getName() + "|" +
                        item.getDescription() + "|" +
                        item.getQuantity() + "|" +
                        item.getItemCondition().name() + "|" +
                        safeString(
                                item.getLastMaintained() == null
                                        ? null
                                        : item.getLastMaintained().toString()
                        )
                );

                writer.newLine();
            }

        } catch (IOException e) {
            System.out.println(
                    "Error saving inventory records: " + e.getMessage()
            );
        }
    }

    public static ArrayList<ItemEntity> loadInventoryFromTextFile(
            String filename) {

        ArrayList<ItemEntity> inventory = new ArrayList<>();

        File file = new File(filename);

        if (!file.exists()) {
            return inventory;
        }

        try (BufferedReader reader =
                     new BufferedReader(new FileReader(filename))) {

            String line;

            while ((line = reader.readLine()) != null) {

                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] fields = line.split("\\|", -1);

                if (fields.length < 6) {
                    System.out.println(
                            "Skipping invalid inventory record."
                    );
                    continue;
                }

                // Parse each record on its own so a single bad quantity/date
                // does not discard the inventory records that come after it.
                try {
                    int id = Integer.parseInt(fields[0]);
                    String name = fields[1];
                    String description = fields[2];
                    int quantity = Integer.parseInt(fields[3]);

                    ItemCondition condition =
                            ItemCondition.setCondition(fields[4]);

                    LocalDate lastMaintained = null;

                    if (!fields[5].isEmpty()) {
                        lastMaintained =
                                LocalDate.parse(fields[5]);
                    }

                    if (condition == null) {
                        System.out.println(
                                "Skipping inventory record with invalid condition."
                        );
                        continue;
                    }

                    ItemEntity item = new ItemEntity(
                            id,
                            condition.ordinal(),
                            name,
                            description,
                            quantity
                    );

                    item.setLastMaintained(lastMaintained);

                    inventory.add(item);

                } catch (NumberFormatException | java.time.format.DateTimeParseException e) {
                    System.out.println(
                            "Skipping inventory record with invalid data: " + line
                    );
                }
            }

        } catch (IOException e) {
            System.out.println(
                    "Error loading inventory records: " + e.getMessage()
            );
        }

        return inventory;
    }


    // ==============================
    // HELPER METHODS
    // ==============================

    private static ArrayList<String> toArrayList(String value) {

        if (value == null || value.isEmpty()) {
            return new ArrayList<>();
        }

        return new ArrayList<>(
                Arrays.asList(value.split(","))
        );
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty()
                ? null
                : value;
    }

}