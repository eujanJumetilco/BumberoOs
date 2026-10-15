package com.groupnine.bumberoos.security;

import com.groupnine.bumberoos.domain.enums.UserRole;
import org.springframework.security.core.Authentication;

import java.io.Serializable;

/** Who is signed in. Kept in the HTTP session as the authentication principal. */
public record AuthenticatedAccount(Long id, String email, UserRole role) implements Serializable {

    /** The signed-in account's id, or null for anonymous callers and non-session principals. */
    public static Long idOf(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedAccount a ? a.id() : null;
    }
}
