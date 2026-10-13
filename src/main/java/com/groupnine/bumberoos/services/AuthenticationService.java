package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import org.springframework.stereotype.Service;
import com.groupnine.bumberoos.domain.exceptions.InvalidCredentialException;
import com.groupnine.bumberoos.domain.exceptions.EmailAlreadyInUseException;

import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AuthenticationService {

    // ========== DEPENDENCY SETUP ========== //

    private final StaffService staffService;

    public AuthenticationService(
        StaffService staffService
    ){
        this.staffService = staffService;
    }


    // ========== SERVICE PROPER ========== //

    // returns false if no admin is registered, should trigger the ui to prompt the user
    // to create an account, otherwise present the user with a log in screen
    public Boolean isRegistered(){
        ArrayList<StaffEntity> adminList = this.getAdminList();

        return !adminList.isEmpty();
    }

    // logs in user based on email and password
    public StaffEntity signIn(String email, String password) throws Exception {
        this.validateEmailAndPassword(email, password);

        ArrayList<StaffEntity> adminList = this.getAdminList();
        Optional<StaffEntity> account = adminList.stream()
                .filter(admin ->
                    admin.getEmail().equalsIgnoreCase(email)
                        && admin.getPassword().equals(password)
                )
                .findFirst();

        if (account.isPresent()) return account.get();
        else throw new Exception("please try logging in again");
    }

    public StaffEntity signUp(
            String email, String password, String name, String contactNumber,
            String address, boolean isEmployed, ArrayList<String> trainings,
            ArrayList<String> certifications
    ) throws Exception {
        this.validateEmailAndPassword(email, password);

        // Check if account with same email already exists
        ArrayList<StaffEntity> adminList = this.getAdminList();
        Optional<StaffEntity> accountWithSameEmail = adminList.stream()
                .filter(admin -> admin.getEmail().equalsIgnoreCase(email))
                .findFirst();
        if (accountWithSameEmail.isPresent()) throw new EmailAlreadyInUseException("please use another email address");

        // Save admin data and write into txt file for saving and return data of newAdmin
        StaffEntity newAdmin = new StaffEntity(
                true, name, "Admin", contactNumber, address,
                isEmployed, trainings, certifications
        );

        return null; // PLACEHOLDER ONLY, WRITE THIS PROPERLY
    }


    // ========== HELPER METHODS ========== //

    private ArrayList<StaffEntity> getAdminList(){
        ArrayList<StaffEntity> adminList = staffService.getStaffList();

        return adminList.stream()
                .filter(staff ->
                        (staff.isAdmin()
                                && staff.getEmail() != null
                                && !staff.getEmail().trim().isEmpty()
                                && staff.getPassword() != null
                                && !staff.getPassword().trim().isEmpty()
                        )
                )
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void validateEmailAndPassword(String email, String password) throws Exception{
        if (email == null || password == null || email.isBlank() || password.isBlank()) {
            throw new InvalidCredentialException("input proper email and/or password fields");
        }
        if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw new InvalidCredentialException("input a proper email address");
        }
    }

}
