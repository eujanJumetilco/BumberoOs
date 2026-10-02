package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.domain.exceptions.InvalidCredentialException;
import org.springframework.stereotype.Service;

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

    public Boolean isRegistered(){
        ArrayList<StaffEntity> adminList = this.getAdminList();

        return !adminList.isEmpty();
    }

    public StaffEntity logIn(String email, String password) throws Exception {
        if (email == null || password == null || email.isBlank() || password.isBlank()) {
            throw new InvalidCredentialException("Input proper email and/or password fields");
        }
        if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw new InvalidCredentialException("Input a proper email address");
        }

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

    // ========== HELPED METHODS ========== //

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

}
