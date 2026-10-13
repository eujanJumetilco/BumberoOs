package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.UserAccountEntity;
import com.groupnine.bumberoos.domain.enums.UserRole;
import com.groupnine.bumberoos.domain.exceptions.EmailAlreadyInUseException;
import com.groupnine.bumberoos.domain.exceptions.InvalidCredentialException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Mocked UserAccountService, real BCrypt encoder. */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String EMAIL = "admin@bumberoos.com";
    private static final String PASSWORD = "secret123";

    @Mock
    private UserAccountService userAccountService;

    @Spy
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @InjectMocks
    private AuthenticationService authenticationService;

    private UserAccountEntity account(boolean enabled) {
        UserAccountEntity a = new UserAccountEntity(
                "Admin@Bumberoos.com", passwordEncoder.encode(PASSWORD), UserRole.ADMIN, "maria santos");
        a.setEnabled(enabled);
        return a;
    }

    // ===== isRegistered ===== //

    @Test
    void isRegistered_reflectsWhetherAnEnabledAdminExists() {
        when(userAccountService.hasEnabledAdmin()).thenReturn(false, true);

        assertFalse(authenticationService.isRegistered());
        assertTrue(authenticationService.isRegistered());
    }

    // ===== signIn: success ===== //

    @Test
    void signIn_returnsAccount_whenCredentialsMatch() throws Exception {
        UserAccountEntity a = account(true);
        when(userAccountService.findByEmail(EMAIL)).thenReturn(Optional.of(a));

        assertSame(a, authenticationService.signIn(EMAIL, PASSWORD));
    }

    // ===== signIn: validation (never touches the service) ===== //

    @Test
    void signIn_rejectsBadInput() {
        assertThrows(InvalidCredentialException.class, () -> authenticationService.signIn("", PASSWORD));
        assertThrows(InvalidCredentialException.class, () -> authenticationService.signIn(EMAIL, ""));
        assertThrows(InvalidCredentialException.class, () -> authenticationService.signIn(null, PASSWORD));
        assertThrows(InvalidCredentialException.class, () -> authenticationService.signIn(EMAIL, null));
        assertThrows(InvalidCredentialException.class, () -> authenticationService.signIn("adminbumberoos.com", PASSWORD));
        assertThrows(InvalidCredentialException.class, () -> authenticationService.signIn("admin@", PASSWORD));
        assertThrows(InvalidCredentialException.class, () -> authenticationService.signIn(" admin@bumberoos.com ", PASSWORD));
        verifyNoInteractions(userAccountService);
    }

    // ===== signIn: failures ===== //

    private void assertPlainLoginFailure(String email, String password) {
        Exception ex = assertThrows(Exception.class, () -> authenticationService.signIn(email, password));
        assertEquals(Exception.class, ex.getClass());
        assertEquals("please try logging in again", ex.getMessage());
    }

    @Test
    void signIn_fails_onWrongOrWrongCasePassword() {
        UserAccountEntity a = account(true);
        when(userAccountService.findByEmail(EMAIL)).thenReturn(Optional.of(a));

        assertPlainLoginFailure(EMAIL, "wrongpass");
        assertPlainLoginFailure(EMAIL, "SECRET123");
    }

    @Test
    void signIn_fails_onUnknownEmail() {
        when(userAccountService.findByEmail("nobody@bumberoos.com")).thenReturn(Optional.empty());

        assertPlainLoginFailure("nobody@bumberoos.com", PASSWORD);
    }

    @Test
    void signIn_fails_whenAccountDisabled() {
        UserAccountEntity disabled = account(false);
        when(userAccountService.findByEmail(EMAIL)).thenReturn(Optional.of(disabled));

        assertPlainLoginFailure(EMAIL, PASSWORD);
    }

    // ===== signUp ===== //

    @Test
    void signUp_savesAccountWithHashedPasswordAndNormalizedFields() throws Exception {
        when(userAccountService.emailExists("Admin@Bumberoos.com")).thenReturn(false);
        when(userAccountService.addAccount(any(UserAccountEntity.class))).thenAnswer(i -> i.getArgument(0));

        UserAccountEntity result = authenticationService.signUp(
                "Admin@Bumberoos.com", PASSWORD, "maria santos", UserRole.ADMIN);

        assertEquals("admin@bumberoos.com", result.getEmail());
        assertEquals("Maria Santos", result.getDisplayName());
        assertEquals(UserRole.ADMIN, result.getRole());
        assertTrue(result.isEnabled());
        assertNotEquals(PASSWORD, result.getPasswordHash());
        assertTrue(passwordEncoder.matches(PASSWORD, result.getPasswordHash()));
    }

    @Test
    void signUp_defaultsToAdminWhenRoleIsNull() throws Exception {
        when(userAccountService.emailExists(any())).thenReturn(false);
        when(userAccountService.addAccount(any(UserAccountEntity.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(UserRole.ADMIN, authenticationService.signUp(EMAIL, PASSWORD, "x", null).getRole());
    }

    @Test
    void signUp_rejectsDuplicateEmail() {
        when(userAccountService.emailExists(EMAIL)).thenReturn(true);

        assertThrows(EmailAlreadyInUseException.class,
                () -> authenticationService.signUp(EMAIL, PASSWORD, "x", UserRole.ADMIN));
        verify(userAccountService, never()).addAccount(any());
    }

    @Test
    void signUp_rejectsInvalidInputBeforeTouchingService() {
        assertThrows(InvalidCredentialException.class,
                () -> authenticationService.signUp("not-an-email", PASSWORD, "x", UserRole.ADMIN));
        assertThrows(InvalidCredentialException.class,
                () -> authenticationService.signUp(EMAIL, "", "x", UserRole.ADMIN));
        verifyNoInteractions(userAccountService);
    }
}
