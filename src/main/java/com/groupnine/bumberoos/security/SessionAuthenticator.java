package com.groupnine.bumberoos.security;

import com.groupnine.bumberoos.domain.entities.UserAccountEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/** Starts and ends the cookie-backed login session. */
@Component
public class SessionAuthenticator {

    private final HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();

    /** Signs the account in for this browser session. A new session id is issued to prevent fixation. */
    public void login(UserAccountEntity account, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticationFor(account.getId(), account.getEmail(), account.getRole()));
        SecurityContextHolder.setContext(context);
        repository.saveContext(context, request, response);
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
    }

    static UsernamePasswordAuthenticationToken authenticationFor(
            Long id, String email, com.groupnine.bumberoos.domain.enums.UserRole role
    ) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedAccount(id, email, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}
