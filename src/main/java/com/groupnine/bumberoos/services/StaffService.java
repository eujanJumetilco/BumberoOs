package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.util.TextStorageUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class StaffService {

    private static final String FILE_NAME = "data/staff_data.txt";

    private final ArrayList<StaffEntity> staffList = new ArrayList<>();
    private int nextId = 1;

    public StaffService() {
        ArrayList<StaffEntity> loaded = TextStorageUtil.loadStaffFromTextFile(FILE_NAME);
        staffList.addAll(loaded);

        for (StaffEntity s : staffList) {
            if (s.getId() >= nextId) {
                nextId = s.getId() + 1;
            }
        }
    }

    public void saveAll() {
        TextStorageUtil.saveStaffToTextFile(staffList, FILE_NAME);
    }

    public int getNextId() {
        return nextId;
    }


    // =====> ADD <===== //

    public StaffEntity addStaff(StaffEntity staff) {
        // Assign a unique ID via a simple counter
        staff.setId(nextId++);
        staffList.add(staff);
        saveAll();
        return staff;
    }


    // =====> REMOVE <===== //

    /**
     * Removes a staff member by ID.
     * @return true if found and removed, false otherwise.
     */
public boolean removeStaff(int id) {
    boolean removed = staffList.removeIf(s -> s.getId() == id);

    if (removed) {
        saveAll();
    }

    return removed;
}


    // =====> EDIT <===== //

    /**
     * Overwrites editable fields on an existing staff record.
     * Pass null / the existing value to leave a field unchanged.
     *
     * @param id            ID of the staff member to update
     * @param name          new name, or null to keep current
     * @param role          new role, or null to keep current
     * @param contactNumber new contact number, or null to keep current
     * @param address       new address, or null to keep current
     * @param isEmployed    new employment status
     * @param trainings     new trainings list, or null to keep current
     * @param certifications new certifications list, or null to keep current
     * @param availabilityCode 0=UNAVAILABLE, 1=AVAILABLE, 2=DISPATCHED; -1 to keep current
     * @return the updated StaffEntity, or null if not found
     */
    public StaffEntity editStaff(
            int id,
            String name,
            String role,
            String contactNumber,
            String address,
            Boolean isEmployed,
            ArrayList<String> trainings,
            ArrayList<String> certifications,
            int availabilityCode
    ) {
        StaffEntity staff = findById(id);
        if (staff == null) return null;

        if (name          != null) staff.setName(name);
        if (role          != null) staff.setRole(role);
        if (contactNumber != null) staff.setContactNumber(contactNumber);
        if (address       != null) staff.setAddress(address);
        if (isEmployed    != null) staff.setEmployed(isEmployed);
        if (trainings     != null) staff.setTrainings(trainings);
        if (certifications!= null) staff.setCertifications(certifications);
        if (availabilityCode >= 0) staff.setStaffAvailability(availabilityCode);

        saveAll();
        return staff;
    }


    // =====> QUERIES <===== //

    public StaffEntity findById(int id) {
        return staffList.stream()
                .filter(s -> s.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public ArrayList<StaffEntity> getAllStaff() {
        return new ArrayList<>(staffList);
    }


}