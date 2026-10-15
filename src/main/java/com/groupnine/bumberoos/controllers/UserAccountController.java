package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.domain.enums.UserRole;
import com.groupnine.bumberoos.dto.UserAccountResponse;
import com.groupnine.bumberoos.mappers.UserAccountMapper;
import com.groupnine.bumberoos.security.AuthenticatedAccount;
import com.groupnine.bumberoos.services.AccountAdministrationService;
import com.groupnine.bumberoos.services.StaffService;
import com.groupnine.bumberoos.services.UserAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class UserAccountController {

    // ------------------ DEPENDENCY SETUP ------------------ //

    private final UserAccountService userAccountService;
    private final AccountAdministrationService accountAdministration;
    private final StaffService staffService;
    private final UserAccountMapper userAccountMapper;

    public UserAccountController(
            UserAccountService userAccountService,
            AccountAdministrationService accountAdministration,
            StaffService staffService,
            UserAccountMapper userAccountMapper
    ){
        this.userAccountService = userAccountService;
        this.accountAdministration = accountAdministration;
        this.staffService = staffService;
        this.userAccountMapper = userAccountMapper;
    }

    // ------------------ CONTROLLERS ------------------ //

    @GetMapping
    public List<UserAccountResponse> getAll() {
        return userAccountMapper.toResponses(userAccountService.getAllAccounts());
    }

    @GetMapping("/{id}")
    public UserAccountResponse getById(@PathVariable Long id) {
        return userAccountService.findById(id).map(userAccountMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    @PatchMapping("/{id}/enabled")
    public UserAccountResponse setEnabled(
            @PathVariable Long id, @RequestParam boolean value, Authentication authentication
    ) {
        return accountAdministration.setEnabled(AuthenticatedAccount.idOf(authentication), id, value)
                .map(userAccountMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    @PatchMapping("/{id}/role")
    public UserAccountResponse setRole(
            @PathVariable Long id, @RequestParam UserRole value, Authentication authentication
    ) {
        return accountAdministration.setRole(AuthenticatedAccount.idOf(authentication), id, value)
                .map(userAccountMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    /** Links the account to a staff member. */
    @PutMapping("/{id}/staff/{staffId}")
    public UserAccountResponse linkStaff(@PathVariable Long id, @PathVariable Long staffId) {
        var staff = staffService.findById(staffId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "staff " + staffId + " not found"));
        return userAccountService.linkStaff(id, staff).map(userAccountMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    @DeleteMapping("/{id}/staff")
    public UserAccountResponse unlinkStaff(@PathVariable Long id) {
        return userAccountService.linkStaff(id, null).map(userAccountMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id, Authentication authentication) {
        if (!accountAdministration.remove(AuthenticatedAccount.idOf(authentication), id)) throw notFound(id);
    }

    private ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "account " + id + " not found");
    }
}
