package com.groupnine.bumberoos.util;

import java.io.*;

/**
 * Handles BINARY input/output of system-level, structured information.
 *
 * Per lab requirements, this does NOT duplicate the full text record files.
 * Instead it stores small application/system-level data:
 *   - record counters for each module
 *   - the next numeric ID to be generated for each module
 *   - the application version
 *   - auto-save preference (sample user setting)
 *
 * Values are written/read with DataOutputStream / DataInputStream in a
 * fixed sequence: int, int, int, int, int, int, String, boolean.
 * They MUST be read back in the exact same order and types they were
 * written in.
 */
public class BinaryStorageUtil {

    private static final String APP_VERSION = "1.0.0";

    public static void saveSystemData(
            String filename,
            int staffCount,
            int inventoryCount,
            int incidentCount,
            int nextStaffId,
            int nextInventoryId,
            int nextIncidentId,
            boolean autoSaveEnabled
    ) {
        File file = new File(filename);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (DataOutputStream out =
                     new DataOutputStream(new FileOutputStream(filename))) {

            out.writeInt(staffCount);
            out.writeInt(inventoryCount);
            out.writeInt(incidentCount);
            out.writeInt(nextStaffId);
            out.writeInt(nextInventoryId);
            out.writeInt(nextIncidentId);
            out.writeUTF(APP_VERSION);
            out.writeBoolean(autoSaveEnabled);

        } catch (IOException e) {
            System.out.println("Error saving system data: " + e.getMessage());
        }
    }

    public static SystemData loadSystemData(String filename) {
        File file = new File(filename);

        if (!file.exists()) {
            return new SystemData(0, 0, 0, 1, 1, 1, APP_VERSION, true);
        }

        try (DataInputStream in =
                     new DataInputStream(new FileInputStream(filename))) {

            int staffCount = in.readInt();
            int inventoryCount = in.readInt();
            int incidentCount = in.readInt();
            int nextStaffId = in.readInt();
            int nextInventoryId = in.readInt();
            int nextIncidentId = in.readInt();
            String version = in.readUTF();
            boolean autoSaveEnabled = in.readBoolean();

            return new SystemData(
                    staffCount, inventoryCount, incidentCount,
                    nextStaffId, nextInventoryId, nextIncidentId,
                    version, autoSaveEnabled
            );

        } catch (IOException e) {
            System.out.println("Error loading system data: " + e.getMessage());
            return new SystemData(0, 0, 0, 1, 1, 1, APP_VERSION, true);
        }
    }

    /** Simple carrier for the values read back from the binary system file. */
    public record SystemData(
            int staffCount,
            int inventoryCount,
            int incidentCount,
            int nextStaffId,
            int nextInventoryId,
            int nextIncidentId,
            String appVersion,
            boolean autoSaveEnabled
    ) {}
}
