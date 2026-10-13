package com.groupnine.bumberoos.domain.entities;

import com.groupnine.bumberoos.domain.enums.StaffAvailability;
import com.groupnine.bumberoos.util.StringUtil;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;

public class StaffEntity implements Serializable {

    private int id;
    private StaffAvailability staffAvailability;
    private boolean isAdmin;

    private String password;
    private String email;

    private String name;
    private String role;
    private String contactNumber;
    private String address;
    private boolean isEmployed;

    private ArrayList<String> trainings;
    private ArrayList<String> certifications;

    private LocalDate dateJoined;

    public StaffEntity(
        boolean isAdmin, String name, String role, String contactNumber,
        String address, boolean isEmployed, ArrayList<String> trainings,
        ArrayList<String> certifications
    ){
        setAdmin(isAdmin);
        setName(name);
        setRole(role);
        setContactNumber(contactNumber);
        setAddress(address);
        setEmployed(isEmployed);
        setTrainings(trainings);
        setCertifications(certifications);
        setStaffAvailability(0);
        this.dateJoined = LocalDate.now();
    }

    public void setAdminCredentials(String email, String password){
        this.isAdmin = true;
        this.email = StringUtil.normalizeEmail(email);
        this.password = password;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public StaffAvailability getStaffAvailability() {
        return staffAvailability;
    }

    public String getStaffAvailabilityAsString(){
        if (this.staffAvailability == null) return "Unknown";

        return switch(this.staffAvailability) {
            case UNAVAILABLE -> "Unavailable";
            case AVAILABLE -> "Available";
            case DISPATCHED -> "Dispatched";
        };
    }

    public void setStaffAvailability(int input) {
        this.staffAvailability = StaffAvailability.setAvailability(input);
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = StringUtil.toTitleCase(name);
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = StringUtil.toTitleCase(role);
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = StringUtil.normalizePhoneNumber(contactNumber);
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = StringUtil.capitalizeFirstLetter(address);
    }

    public boolean isEmployed() {
        return isEmployed;
    }

    public void setEmployed(boolean employed) {
        isEmployed = employed;
    }

    public ArrayList<String> getTrainings() {
        return trainings;
    }

    public void setTrainings(ArrayList<String> trainings) {
        this.trainings = StringUtil.cleanList(trainings);
    }

    public ArrayList<String> getCertifications() {
        return certifications;
    }

    public void setCertifications(ArrayList<String> certifications) {
        this.certifications = StringUtil.cleanList(certifications);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = StringUtil.normalizeEmail(email);
    }

    public LocalDate getDateJoined() {
        return dateJoined;
    }
}