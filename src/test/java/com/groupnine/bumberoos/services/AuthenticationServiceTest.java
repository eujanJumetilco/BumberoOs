package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.domain.exceptions.InvalidCredentialException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    // ===== PASS / FAIL REPORTING ===== //

    private static int passed = 0;
    private static int failed = 0;

    @RegisterExtension
    static TestWatcher watcher = new TestWatcher() {
        @Override
        public void testSuccessful(ExtensionContext context) {
            passed++;
            System.out.println("  [PASS] " + context.getDisplayName());
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            failed++;
            System.out.println("  [FAIL] " + context.getDisplayName());
            System.out.println("         Reason: " + cause);
        }
    };

    @BeforeAll
    static void printHeader() {
        System.out.println("\n===== AuthenticationService test start =====");
    }

    @AfterAll
    static void printSummary() {
        System.out.println("\n===== AuthenticationService test end =====");
        System.out.println("Passed: " + passed + " | Failed: " + failed
                + " | Total: " + (passed + failed) + "\n");
    }

    @BeforeEach
    void printTestName(TestInfo info) {
        System.out.println("\n> Testing: " + info.getDisplayName());
    }

    // ===== MOCKS ===== //

    @Mock
    private StaffService staffService;

    @InjectMocks
    private AuthenticationService authenticationService;

    // ===== MOCK DATA HELPERS ===== //

    private static final String ADMIN_EMAIL = "admin@bumberoos.com";
    private static final String ADMIN_PASSWORD = "secret123";

    /** Regular staff member: not an admin, no credentials. */
    private StaffEntity regularStaff() {
        return new StaffEntity(
                false, "juan dela cruz", "driver", "09171234567",
                "paranaque city", true,
                new ArrayList<>(List.of("First Aid")),
                new ArrayList<>()
        );
    }

    /** Fully registered admin: admin flag plus email and password. */
    private StaffEntity adminWithCredentials() {
        StaffEntity admin = new StaffEntity(
                true, "maria santos", "chief", "09181234567",
                "makati city", true,
                new ArrayList<>(),
                new ArrayList<>()
        );
        admin.setAdminCredentials("Admin@Bumberoos.com", ADMIN_PASSWORD);
        return admin;
    }

    /** A second, different admin, for tests with multiple admins. */
    private StaffEntity secondAdmin() {
        StaffEntity admin = new StaffEntity(
                true, "jose rizal", "deputy", "09201234567",
                "manila city", true,
                new ArrayList<>(),
                new ArrayList<>()
        );
        admin.setAdminCredentials("jose@bumberoos.com", "other456");
        return admin;
    }

    /** Admin flag is set but no email/password was ever assigned (both null). */
    private StaffEntity adminWithoutCredentials() {
        return new StaffEntity(
                true, "pedro reyes", "supervisor", "09191234567",
                "pasay city", true,
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

    /** Admin whose credentials are blank strings. */
    private StaffEntity adminWithBlankCredentials() {
        StaffEntity admin = adminWithoutCredentials();
        admin.setAdminCredentials("", "");
        return admin;
    }

    /** NOT an admin, but has an email and password set via the plain setters. */
    private StaffEntity regularStaffWithCredentials() {
        StaffEntity staff = regularStaff();
        staff.setEmail(ADMIN_EMAIL);
        staff.setPassword(ADMIN_PASSWORD);
        return staff;
    }

    /** Prints one line describing a mock staff record. */
    private void describe(StaffEntity s) {
        System.out.println("  Mock staff: " + s.getName()
                + " | admin=" + s.isAdmin()
                + " | email=" + s.getEmail()
                + " | password=" + (s.getPassword() == null ? null : "'" + s.getPassword() + "'"));
    }

    /** Stubs the mocked StaffService and prints what it will return. */
    private void givenStaff(StaffEntity... staff) {
        System.out.println("  Staff list size: " + staff.length);
        for (StaffEntity s : staff) describe(s);
        when(staffService.getStaffList()).thenReturn(new ArrayList<>(List.of(staff)));
    }

    /** Asserts an exception of the given type and prints it. */
    private <T extends Throwable> T expectThrown(Class<T> type, Executable action) {
        T ex = assertThrows(type, action);
        System.out.println("  Threw (expected): " + ex.getClass().getSimpleName()
                + " - " + ex.getMessage());
        return ex;
    }

    // ===== isRegistered() TESTS ===== //

    @Test
    @DisplayName("isRegistered() -> false when staff list is empty")
    void isRegistered_returnsFalse_whenStaffListIsEmpty() {
        givenStaff();

        boolean result = authenticationService.isRegistered();

        System.out.println("  Expected: false | Actual: " + result);
        assertFalse(result);
    }

    @Test
    @DisplayName("isRegistered() -> false when only regular staff exist")
    void isRegistered_returnsFalse_whenOnlyRegularStaffExist() {
        givenStaff(regularStaff(), regularStaff());

        boolean result = authenticationService.isRegistered();

        System.out.println("  Expected: false | Actual: " + result);
        assertFalse(result);
    }

    @Test
    @DisplayName("isRegistered() -> true when an admin with credentials exists")
    void isRegistered_returnsTrue_whenAdminWithCredentialsExists() {
        givenStaff(adminWithCredentials());

        boolean result = authenticationService.isRegistered();

        System.out.println("  Expected: true | Actual: " + result);
        assertTrue(result);
    }

    @Test
    @DisplayName("isRegistered() -> true when admin is mixed in with regular staff")
    void isRegistered_returnsTrue_whenAdminIsMixedInWithRegularStaff() {
        givenStaff(regularStaff(), adminWithCredentials(), regularStaff());

        boolean result = authenticationService.isRegistered();

        System.out.println("  Expected: true | Actual: " + result);
        assertTrue(result);
    }

    @Test
    @DisplayName("isRegistered() -> false when admin has blank credentials")
    void isRegistered_returnsFalse_whenAdminHasBlankCredentials() {
        givenStaff(adminWithBlankCredentials());

        boolean result = authenticationService.isRegistered();

        System.out.println("  Expected: false | Actual: " + result);
        assertFalse(result);
    }

    @Test
    @DisplayName("isRegistered() -> false when admin has null credentials")
    void isRegistered_returnsFalse_whenAdminHasNullCredentials() {
        givenStaff(adminWithoutCredentials());

        boolean result = authenticationService.isRegistered();

        System.out.println("  Expected: false | Actual: " + result);
        assertFalse(result);
    }

    @Test
    @DisplayName("isRegistered() -> reads staff from StaffService")
    void isRegistered_readsStaffFromStaffService() {
        givenStaff();

        authenticationService.isRegistered();

        verify(staffService).getStaffList();
        System.out.println("  Verified: StaffService.getStaffList() was called");
    }

    // ===== logIn() TESTS: SUCCESS ===== //

    @Test
    @DisplayName("logIn() -> returns the admin when email and password match")
    void logIn_returnsAdmin_whenCredentialsMatch() throws Exception {
        StaffEntity admin = adminWithCredentials();
        givenStaff(admin);

        System.out.println("  Attempt: " + ADMIN_EMAIL + " / " + ADMIN_PASSWORD);
        StaffEntity result = authenticationService.signIn(ADMIN_EMAIL, ADMIN_PASSWORD);

        System.out.println("  Expected: Maria Santos | Actual: " + result.getName());
        assertSame(admin, result);
    }

    @Test
    @DisplayName("logIn() -> email match is case-insensitive")
    void logIn_ignoresEmailCase() throws Exception {
        StaffEntity admin = adminWithCredentials();
        givenStaff(admin);

        System.out.println("  Attempt: ADMIN@BUMBEROOS.COM / " + ADMIN_PASSWORD);
        StaffEntity result = authenticationService.signIn("ADMIN@BUMBEROOS.COM", ADMIN_PASSWORD);

        System.out.println("  Expected: Maria Santos | Actual: " + result.getName());
        assertSame(admin, result);
    }

    @Test
    @DisplayName("logIn() -> picks the correct admin when several exist")
    void logIn_returnsCorrectAdmin_whenMultipleAdminsExist() throws Exception {
        StaffEntity first = adminWithCredentials();
        StaffEntity second = secondAdmin();
        givenStaff(regularStaff(), first, second);

        System.out.println("  Attempt: jose@bumberoos.com / other456");
        StaffEntity result = authenticationService.signIn("jose@bumberoos.com", "other456");

        System.out.println("  Expected: Jose Rizal | Actual: " + result.getName());
        assertSame(second, result);
    }

    @Test
    @DisplayName("logIn() -> skips admins with null credentials without crashing")
    void logIn_skipsAdminsWithNullCredentials() throws Exception {
        StaffEntity admin = adminWithCredentials();
        givenStaff(adminWithoutCredentials(), admin);

        System.out.println("  Attempt: " + ADMIN_EMAIL + " / " + ADMIN_PASSWORD);
        StaffEntity result = authenticationService.signIn(ADMIN_EMAIL, ADMIN_PASSWORD);

        System.out.println("  Expected: Maria Santos | Actual: " + result.getName());
        assertSame(admin, result);
    }

    // ===== logIn() TESTS: INPUT VALIDATION ===== //
    // These throw before touching StaffService, so nothing is stubbed.

    @Test
    @DisplayName("logIn() -> InvalidCredentialException on empty email")
    void logIn_throwsInvalidCredential_onEmptyEmail() {
        System.out.println("  Attempt: '' / " + ADMIN_PASSWORD);

        expectThrown(InvalidCredentialException.class,
                () -> authenticationService.signIn("", ADMIN_PASSWORD));
        verifyNoInteractions(staffService);
    }

    @Test
    @DisplayName("logIn() -> InvalidCredentialException on empty password")
    void logIn_throwsInvalidCredential_onEmptyPassword() {
        System.out.println("  Attempt: " + ADMIN_EMAIL + " / ''");

        expectThrown(InvalidCredentialException.class,
                () -> authenticationService.signIn(ADMIN_EMAIL, ""));
        verifyNoInteractions(staffService);
    }

    @Test
    @DisplayName("logIn() -> InvalidCredentialException on malformed email (no @)")
    void logIn_throwsInvalidCredential_onEmailWithoutAtSign() {
        System.out.println("  Attempt: adminbumberoos.com / " + ADMIN_PASSWORD);

        expectThrown(InvalidCredentialException.class,
                () -> authenticationService.signIn("adminbumberoos.com", ADMIN_PASSWORD));
        verifyNoInteractions(staffService);
    }

    @Test
    @DisplayName("logIn() -> InvalidCredentialException on malformed email (no domain)")
    void logIn_throwsInvalidCredential_onEmailWithoutDomain() {
        System.out.println("  Attempt: admin@ / " + ADMIN_PASSWORD);

        expectThrown(InvalidCredentialException.class,
                () -> authenticationService.signIn("admin@", ADMIN_PASSWORD));
        verifyNoInteractions(staffService);
    }

    @Test
    @DisplayName("logIn() -> InvalidCredentialException on email with spaces")
    void logIn_throwsInvalidCredential_onEmailWithSpaces() {
        System.out.println("  Attempt: ' admin@bumberoos.com ' / " + ADMIN_PASSWORD);

        expectThrown(InvalidCredentialException.class,
                () -> authenticationService.signIn(" admin@bumberoos.com ", ADMIN_PASSWORD));
        verifyNoInteractions(staffService);
    }

    // NOTE: the next two tests FAIL with the current implementation
    // (NullPointerException from email.isEmpty() / password.isEmpty()).
    // See the fix below the code.
    @Test
    @DisplayName("logIn() -> InvalidCredentialException on null email")
    void logIn_throwsInvalidCredential_onNullEmail() {
        System.out.println("  Attempt: null / " + ADMIN_PASSWORD);

        expectThrown(InvalidCredentialException.class,
                () -> authenticationService.signIn(null, ADMIN_PASSWORD));
    }

    @Test
    @DisplayName("logIn() -> InvalidCredentialException on null password")
    void logIn_throwsInvalidCredential_onNullPassword() {
        System.out.println("  Attempt: " + ADMIN_EMAIL + " / null");

        expectThrown(InvalidCredentialException.class,
                () -> authenticationService.signIn(ADMIN_EMAIL, null));
    }

    // ===== logIn() TESTS: FAILED LOGIN ===== //
    // A failed login throws a plain Exception, so these check the exact class
    // (assertThrows(Exception.class) alone would also match InvalidCredentialException).

    private void assertPlainLoginFailure(Executable action) {
        Exception ex = expectThrown(Exception.class, action);
        assertEquals(Exception.class, ex.getClass(),
                "Expected the plain 'try again' Exception, not " + ex.getClass().getSimpleName());
        assertEquals("please try logging in again", ex.getMessage());
    }

    @Test
    @DisplayName("logIn() -> fails when the password is wrong")
    void logIn_fails_onWrongPassword() {
        givenStaff(adminWithCredentials());

        System.out.println("  Attempt: " + ADMIN_EMAIL + " / wrongpass");
        assertPlainLoginFailure(() -> authenticationService.signIn(ADMIN_EMAIL, "wrongpass"));
    }

    @Test
    @DisplayName("logIn() -> password match is case-sensitive")
    void logIn_fails_onPasswordWithWrongCase() {
        givenStaff(adminWithCredentials());

        System.out.println("  Attempt: " + ADMIN_EMAIL + " / SECRET123");
        assertPlainLoginFailure(() -> authenticationService.signIn(ADMIN_EMAIL, "SECRET123"));
    }

    @Test
    @DisplayName("logIn() -> fails when the email is not registered")
    void logIn_fails_onUnknownEmail() {
        givenStaff(adminWithCredentials());

        System.out.println("  Attempt: nobody@bumberoos.com / " + ADMIN_PASSWORD);
        assertPlainLoginFailure(() -> authenticationService.signIn("nobody@bumberoos.com", ADMIN_PASSWORD));
    }

    @Test
    @DisplayName("logIn() -> fails when the staff list is empty")
    void logIn_fails_whenNoStaffExist() {
        givenStaff();

        System.out.println("  Attempt: " + ADMIN_EMAIL + " / " + ADMIN_PASSWORD);
        assertPlainLoginFailure(() -> authenticationService.signIn(ADMIN_EMAIL, ADMIN_PASSWORD));
    }

    @Test
    @DisplayName("logIn() -> fails for a non-admin even with matching credentials")
    void logIn_fails_forNonAdminWithMatchingCredentials() {
        givenStaff(regularStaffWithCredentials());

        System.out.println("  Attempt: " + ADMIN_EMAIL + " / " + ADMIN_PASSWORD);
        assertPlainLoginFailure(() -> authenticationService.signIn(ADMIN_EMAIL, ADMIN_PASSWORD));
    }

    @Test
    @DisplayName("logIn() -> reads staff from StaffService")
    void logIn_readsStaffFromStaffService() throws Exception {
        givenStaff(adminWithCredentials());

        authenticationService.signIn(ADMIN_EMAIL, ADMIN_PASSWORD);

        verify(staffService).getStaffList();
        System.out.println("  Verified: StaffService.getStaffList() was called");
    }
}