package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.domain.entities.UserAccountEntity;
import com.groupnine.bumberoos.domain.enums.UserRole;
import com.groupnine.bumberoos.domain.exceptions.AuthenticationFailedException;
import com.groupnine.bumberoos.dto.SignInRequest;
import com.groupnine.bumberoos.dto.SignUpRequest;
import com.groupnine.bumberoos.dto.UserAccountResponse;
import com.groupnine.bumberoos.mappers.UserAccountMapper;
import com.groupnine.bumberoos.security.AuthenticatedAccount;
import com.groupnine.bumberoos.security.SessionAuthenticator;
import com.groupnine.bumberoos.services.AuthenticationService;
import com.groupnine.bumberoos.services.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // ------------------ DEPENDENCY SETUP ------------------ //

    private final AuthenticationService authenticationService;
    private final UserAccountService userAccountService;
    private final UserAccountMapper userAccountMapper;
    private final SessionAuthenticator sessionAuthenticator;

    public AuthController(
            AuthenticationService authenticationService,
            UserAccountService userAccountService,
            UserAccountMapper userAccountMapper,
            SessionAuthenticator sessionAuthenticator
    ) {
        this.authenticationService = authenticationService;
        this.userAccountService = userAccountService;
        this.userAccountMapper = userAccountMapper;
        this.sessionAuthenticator = sessionAuthenticator;
    }

    // ------------------ CONTROLLERS ------------------ //

    /** False means no admin exists yet, so the UI should show account creation. */
    @GetMapping("/registered")
    public Map<String, Boolean> isRegistered() {
        return Map.of("registered", authenticationService.isRegistered());
    }

    /** Checks the credentials and starts a cookie session. */
    @PostMapping("/signin")
    public UserAccountResponse signIn(
            @Valid @RequestBody SignInRequest request, HttpServletRequest http, HttpServletResponse response
    ) throws Exception {
        UserAccountEntity account;
        try {
            account = authenticationService.signIn(request.email(), request.password());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // the service signals a failed login with a plain Exception
            throw new AuthenticationFailedException(e.getMessage());
        }
        sessionAuthenticator.login(account, http, response);
        return userAccountMapper.toResponse(account);
    }

    /**
     * Creates an account. Security only lets this through for the very first account, or for an admin.
     * The first account is always an ADMIN and is signed in straight away; later accounts are created
     * by an admin and do not change who is signed in.
     */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserAccountResponse signUp(
            @Valid @RequestBody SignUpRequest request, HttpServletRequest http, HttpServletResponse response
    ) throws Exception {
        boolean firstAccount = !userAccountService.hasEnabledAdmin();
        UserAccountEntity account = authenticationService.signUp(
                request.email(), request.password(), request.displayName(),
                firstAccount ? UserRole.ADMIN : request.role());
        if (firstAccount) sessionAuthenticator.login(account, http, response);
        return userAccountMapper.toResponse(account);
    }

    /** The signed-in account, freshly read. 401 when not signed in. */
    @GetMapping("/me")
    public UserAccountResponse me(Authentication authentication) {
        Long id = AuthenticatedAccount.idOf(authentication);
        return userAccountService.findById(id == null ? -1L : id).map(userAccountMapper::toResponse)
                .orElseThrow(() -> new AuthenticationFailedException("please sign in"));
    }

    @PostMapping("/signout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void signOut(HttpServletRequest http) {
        sessionAuthenticator.logout(http);
    }
}
