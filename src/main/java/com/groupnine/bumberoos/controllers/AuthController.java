package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.domain.exceptions.AuthenticationFailedException;
import com.groupnine.bumberoos.dto.SignInRequest;
import com.groupnine.bumberoos.dto.SignUpRequest;
import com.groupnine.bumberoos.dto.UserAccountResponse;
import com.groupnine.bumberoos.mappers.UserAccountMapper;
import com.groupnine.bumberoos.services.AuthenticationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // ------------------ DEPENDENCY SETUP ------------------ //

    private final AuthenticationService authenticationService;
    private final UserAccountMapper userAccountMapper;

    public AuthController(AuthenticationService authenticationService, UserAccountMapper userAccountMapper) {
        this.authenticationService = authenticationService;
        this.userAccountMapper = userAccountMapper;
    }

    // ------------------ CONTROLLERS ------------------ //

    /** False means no admin exists yet, so the UI should show account creation. */
    @GetMapping("/registered")
    public Map<String, Boolean> isRegistered() {
        return Map.of("registered", authenticationService.isRegistered());
    }

    @PostMapping("/signin")
    public UserAccountResponse signIn(@Valid @RequestBody SignInRequest request) throws Exception {
        try {
            return userAccountMapper.toResponse(
                    authenticationService.signIn(request.email(), request.password()));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // the service signals a failed login with a plain Exception
            throw new AuthenticationFailedException(e.getMessage());
        }
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserAccountResponse signUp(@Valid @RequestBody SignUpRequest request) throws Exception {
        return userAccountMapper.toResponse(authenticationService.signUp(
                request.email(), request.password(), request.displayName(), request.role()));
    }
}
