package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.UserAccountEntity;
import com.groupnine.bumberoos.domain.enums.UserRole;
import com.groupnine.bumberoos.domain.exceptions.EmailAlreadyInUseException;
import com.groupnine.bumberoos.domain.exceptions.InvalidCredentialException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    // ========== DEPENDENCY SETUP ========== //

    private final UserAccountService userAccountService;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationService(
        UserAccountService userAccountService,
        PasswordEncoder passwordEncoder
    ){
        this.userAccountService = userAccountService;
        this.passwordEncoder = passwordEncoder;
    }


    // ========== SERVICE PROPER ========== //

    // returns false if no admin is registered, should trigger the ui to prompt the user
    // to create an account, otherwise present the user with a log in screen
    public Boolean isRegistered(){
        return userAccountService.hasEnabledAdmin();
    }

    // logs in user based on email and password
    public UserAccountEntity signIn(String email, String password) throws Exception {
        this.validateEmailAndPassword(email, password);

        return userAccountService.findByEmail(email.trim())
                .filter(UserAccountEntity::isEnabled)
                .filter(account -> passwordEncoder.matches(password, account.getPasswordHash()))
                .orElseThrow(() -> new Exception("please try logging in again"));
    }

    // creates a new account; staff records are linked separately via UserAccountService.linkStaff
    public UserAccountEntity signUp(
            String email, String password, String displayName, UserRole role
    ) throws Exception {
        this.validateEmailAndPassword(email, password);

        if (userAccountService.emailExists(email.trim())) {
            throw new EmailAlreadyInUseException("please use another email address");
        }

        UserAccountEntity account = new UserAccountEntity(
                email, passwordEncoder.encode(password),
                role == null ? UserRole.ADMIN : role, displayName
        );

        return userAccountService.addAccount(account);
    }


    // ========== HELPER METHODS ========== //

    private void validateEmailAndPassword(String email, String password) throws Exception{
        if (email == null || password == null || email.isBlank() || password.isBlank()) {
            throw new InvalidCredentialException("input proper email and/or password fields");
        }
        if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw new InvalidCredentialException("input a proper email address");
        }
    }

}
