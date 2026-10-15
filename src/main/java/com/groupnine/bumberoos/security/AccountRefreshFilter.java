package com.groupnine.bumberoos.security;

import com.groupnine.bumberoos.domain.entities.UserAccountEntity;
import com.groupnine.bumberoos.repositories.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Re-checks the signed-in account on every request, so disabling, deleting or changing the role of an
 * account takes effect immediately instead of when its session expires.
 */
public class AccountRefreshFilter extends OncePerRequestFilter {

    private final UserAccountRepository accounts;

    public AccountRefreshFilter(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && current.getPrincipal() instanceof AuthenticatedAccount signedIn) {
            Optional<UserAccountEntity> account = accounts.findById(signedIn.id());
            if (account.isEmpty() || !account.get().isEnabled()) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) session.invalidate();
            } else if (account.get().getRole() != signedIn.role()) {
                SecurityContextHolder.getContext().setAuthentication(SessionAuthenticator.authenticationFor(
                        signedIn.id(), account.get().getEmail(), account.get().getRole()));
            }
        }
        chain.doFilter(request, response);
    }
}
