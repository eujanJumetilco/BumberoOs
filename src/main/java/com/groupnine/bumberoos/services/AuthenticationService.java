package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.domain.exceptions.EmailAlreadyInUseException;
import com.groupnine.bumberoos.domain.exceptions.InvalidCredentialException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AuthenticationService {

    // ========== DEPENDENCY SETUP ========== //

    private final StaffService staffService;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationService(
        StaffService staffService,
        PasswordEncoder passwordEncoder
    ){
        this.staffService = staffService;
        this.passwordEncoder = passwordEncoder;
    }


    // ========== SERVICE PROPER ========== //

    // returns false if no admin is registered, should trigger the ui to prompt the user
    // to create an account, otherwise present the user with a log in screen
    public Boolean isRegistered(){
        return !this.getAdminList().isEmpty();
    }

    // logs in user based on email and password
    public StaffEntity signIn(String email, String password) throws Exception {
        this.validateEmailAndPassword(email, password);

        Optional<StaffEntity> account = this.getAdminList().stream()
                .filter(admin ->
                    admin.getEmail().equalsIgnoreCase(email)
                        && passwordEncoder.matches(password, admin.getPassword())
                )
                .findFirst();

        if (account.isPresent()) return account.get();
        else throw new Exception("please try logging in again");
    }

    public StaffEntity signUp(
            String email, String password, String name, String contactNumber,
            String address, boolean isEmployed, List<String> trainings,
            List<String> certifications
    ) throws Exception {
        this.validateEmailAndPassword(email, password);

        // Any staff row (admin or not) already holding this email blocks sign up
        boolean emailTaken = staffService.getStaffList().stream()
                .anyMatch(staff -> staff.getEmail() != null && staff.getEmail().equalsIgnoreCase(email.trim()));
        if (emailTaken) throw new EmailAlreadyInUseException("please use another email address");

        StaffEntity newAdmin = new StaffEntity(
                true, name, "Admin", contactNumber, address,
                isEmployed, trainings, certifications
        );
        newAdmin.setAdminCredentials(email, passwordEncoder.encode(password));

        return staffService.addStaff(newAdmin);
    }


    // ========== HELPER METHODS ========== //

    private List<StaffEntity> getAdminList(){
        return staffService.getStaffList().stream()
                .filter(staff ->
                        (staff.isAdmin()
                                && staff.getEmail() != null
                                && !staff.getEmail().trim().isEmpty()
                                && staff.getPassword() != null
                                && !staff.getPassword().trim().isEmpty()
                        )
                )
                .toList();
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
