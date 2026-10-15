package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.domain.entities.UserAccountEntity;
import com.groupnine.bumberoos.domain.enums.UserRole;
import com.groupnine.bumberoos.domain.exceptions.ConflictException;
import com.groupnine.bumberoos.repositories.UserAccountRepository;
import com.groupnine.bumberoos.services.AccountAdministrationService;
import com.groupnine.bumberoos.services.UserAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-in sessions, role enforcement and the account safety rules, end to end on H2. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTest {

    private static final String PASSWORD = "secret123";

    @Autowired MockMvc mvc;
    @Autowired UserAccountService accountService;
    @Autowired UserAccountRepository accountRepository;
    @Autowired AccountAdministrationService administration;
    @Autowired PasswordEncoder encoder;

    private UserAccountEntity account(String email, UserRole role) {
        return accountService.addAccount(new UserAccountEntity(email, encoder.encode(PASSWORD), role, "test"));
    }

    private ResultActions signIn(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private MockHttpSession sessionFor(String email) throws Exception {
        return (MockHttpSession) signIn(email, PASSWORD).andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    // ===== protection ===== //

    @Test
    void api_requiresSignIn() throws Exception {
        mvc.perform(get("/api/staff")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("please sign in")));
        mvc.perform(get("/api/incidents")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void registeredAndSignin_arePublic() throws Exception {
        mvc.perform(get("/api/auth/registered")).andExpect(status().isOk());
        signIn("nobody@test.com", "whatever").andExpect(status().isUnauthorized());
    }

    @Test
    void signIn_withWrongPasswordIs401_andRightPasswordOpensASession() throws Exception {
        account("dispatcher@test.com", UserRole.DISPATCHER);

        signIn("dispatcher@test.com", "wrong-password").andExpect(status().isUnauthorized());

        MockHttpSession session = sessionFor("dispatcher@test.com");
        assertNotNull(session);
        mvc.perform(get("/api/staff").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/staff")).andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountCannotSignIn() throws Exception {
        UserAccountEntity a = account("off@test.com", UserRole.DISPATCHER);
        a.setEnabled(false);

        signIn("off@test.com", PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void me_returnsTheSignedInAccount_andSignOutEndsTheSession() throws Exception {
        account("dispatcher@test.com", UserRole.DISPATCHER);
        MockHttpSession session = sessionFor("dispatcher@test.com");

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("dispatcher@test.com")))
                .andExpect(jsonPath("$.role", is("DISPATCHER")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(post("/api/auth/signout").session(session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/staff").session(session)).andExpect(status().isUnauthorized());
    }

    // ===== roles ===== //

    @Test
    void accountsApi_isAdminOnly() throws Exception {
        account("dispatcher@test.com", UserRole.DISPATCHER);
        account("admin@test.com", UserRole.ADMIN);
        MockHttpSession dispatcher = sessionFor("dispatcher@test.com");
        MockHttpSession admin = sessionFor("admin@test.com");

        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/accounts").session(dispatcher)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", containsString("administrator")));
        mvc.perform(get("/api/accounts").session(admin)).andExpect(status().isOk());
        // Dispatchers still run the rest of the system.
        mvc.perform(get("/api/incidents").session(dispatcher)).andExpect(status().isOk());
    }

    // ===== signup ===== //

    @Test
    void firstSignup_isOpen_forcesAdmin_andSignsIn() throws Exception {
        accountRepository.deleteAll();
        accountRepository.flush();

        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"first@test.com\",\"password\":\"secret123\",\"role\":\"DISPATCHER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("ADMIN")))
                .andReturn().getRequest().getSession(false);

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("first@test.com")));
    }

    @Test
    void laterSignups_needAnAdmin() throws Exception {
        account("admin@test.com", UserRole.ADMIN);
        account("dispatcher@test.com", UserRole.DISPATCHER);
        String body = "{\"email\":\"new@test.com\",\"password\":\"secret123\",\"role\":\"DISPATCHER\"}";

        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body)
                        .session(sessionFor("dispatcher@test.com")))
                .andExpect(status().isForbidden());

        MockHttpSession admin = sessionFor("admin@test.com");
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body).session(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("DISPATCHER")));
        // The admin stays signed in as themselves.
        mvc.perform(get("/api/auth/me").session(admin)).andExpect(jsonPath("$.email", is("admin@test.com")));
    }

    // ===== account changes take effect immediately ===== //

    @Test
    void disablingAnAccount_endsItsExistingSession() throws Exception {
        UserAccountEntity target = account("dispatcher@test.com", UserRole.DISPATCHER);
        account("admin@test.com", UserRole.ADMIN);
        MockHttpSession dispatcher = sessionFor("dispatcher@test.com");
        MockHttpSession admin = sessionFor("admin@test.com");
        mvc.perform(get("/api/staff").session(dispatcher)).andExpect(status().isOk());

        mvc.perform(patch("/api/accounts/" + target.getId() + "/enabled").param("value", "false").session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled", is(false)));

        mvc.perform(get("/api/staff").session(dispatcher)).andExpect(status().isUnauthorized());
    }

    @Test
    void changingARole_appliesToTheLiveSession() throws Exception {
        UserAccountEntity target = account("dispatcher@test.com", UserRole.DISPATCHER);
        account("admin@test.com", UserRole.ADMIN);
        MockHttpSession dispatcher = sessionFor("dispatcher@test.com");
        MockHttpSession admin = sessionFor("admin@test.com");
        mvc.perform(get("/api/accounts").session(dispatcher)).andExpect(status().isForbidden());

        mvc.perform(patch("/api/accounts/" + target.getId() + "/role").param("value", "ADMIN").session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role", is("ADMIN")));
        mvc.perform(get("/api/accounts").session(dispatcher)).andExpect(status().isOk());

        mvc.perform(patch("/api/accounts/" + target.getId() + "/role").param("value", "DISPATCHER").session(admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/accounts").session(dispatcher)).andExpect(status().isForbidden());
    }

    @Test
    void deletingAnAccount_endsItsSession() throws Exception {
        UserAccountEntity target = account("dispatcher@test.com", UserRole.DISPATCHER);
        account("admin@test.com", UserRole.ADMIN);
        MockHttpSession dispatcher = sessionFor("dispatcher@test.com");
        MockHttpSession admin = sessionFor("admin@test.com");

        mvc.perform(delete("/api/accounts/" + target.getId()).session(admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/staff").session(dispatcher)).andExpect(status().isUnauthorized());
    }

    // ===== safety rules ===== //

    @Test
    void anAdmin_cannotDisableDemoteOrDeleteThemselves() throws Exception {
        UserAccountEntity self = account("admin@test.com", UserRole.ADMIN);
        account("other@test.com", UserRole.ADMIN);
        MockHttpSession admin = sessionFor("admin@test.com");
        String url = "/api/accounts/" + self.getId();

        mvc.perform(patch(url + "/enabled").param("value", "false").session(admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error", containsString("own account")));
        mvc.perform(patch(url + "/role").param("value", "DISPATCHER").session(admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error", containsString("own role")));
        mvc.perform(delete(url).session(admin)).andExpect(status().isConflict());
        // Re-enabling or keeping the same role is harmless.
        mvc.perform(patch(url + "/enabled").param("value", "true").session(admin)).andExpect(status().isOk());
        mvc.perform(patch(url + "/role").param("value", "ADMIN").session(admin)).andExpect(status().isOk());
    }

    @Test
    void theLastEnabledAdmin_cannotBeDisabledDemotedOrDeleted() {
        UserAccountEntity only = account("only@test.com", UserRole.ADMIN);
        Long id = only.getId();

        assertThrows(ConflictException.class, () -> administration.setEnabled(null, id, false));
        assertThrows(ConflictException.class, () -> administration.setRole(null, id, UserRole.DISPATCHER));
        assertThrows(ConflictException.class, () -> administration.remove(null, id));
        assertTrue(only.isEnabled());
        assertEquals(UserRole.ADMIN, only.getRole());
    }

    @Test
    void withASecondAdmin_theFirstCanBeChanged() {
        UserAccountEntity first = account("first@test.com", UserRole.ADMIN);
        account("second@test.com", UserRole.ADMIN);

        assertFalse(administration.setEnabled(null, first.getId(), false).orElseThrow().isEnabled());
        // Now second is the last one.
        UserAccountEntity second = accountRepository.findByEmailIgnoreCase("second@test.com").orElseThrow();
        assertThrows(ConflictException.class, () -> administration.setEnabled(null, second.getId(), false));
    }

    @Test
    void unknownAccountsAre404() throws Exception {
        account("admin@test.com", UserRole.ADMIN);
        MockHttpSession admin = sessionFor("admin@test.com");

        mvc.perform(patch("/api/accounts/999999/enabled").param("value", "false").session(admin))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/accounts/999999/role").param("value", "ADMIN").session(admin))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/accounts/999999").session(admin)).andExpect(status().isNotFound());
    }

    // ===== linking accounts to staff ===== //

    @Test
    void linkStaff_isOneToOne_andCanBeUnlinked() throws Exception {
        account("admin@test.com", UserRole.ADMIN);
        UserAccountEntity a = account("a@test.com", UserRole.DISPATCHER);
        UserAccountEntity b = account("b@test.com", UserRole.DISPATCHER);
        long staffId = ((Number) com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/staff").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"ana reyes\",\"role\":\"driver\",\"employed\":true}")
                                .session(sessionFor("admin@test.com")))
                        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        MockHttpSession admin = sessionFor("admin@test.com");

        mvc.perform(put("/api/accounts/" + a.getId() + "/staff/" + staffId).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.staffId", is((int) staffId)));
        mvc.perform(put("/api/accounts/" + a.getId() + "/staff/" + staffId).session(admin))
                .andExpect(status().isOk());
        mvc.perform(put("/api/accounts/" + b.getId() + "/staff/" + staffId).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("a@test.com")));

        mvc.perform(delete("/api/accounts/" + a.getId() + "/staff").session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.staffId", nullValue()));
        mvc.perform(put("/api/accounts/" + b.getId() + "/staff/" + staffId).session(admin))
                .andExpect(status().isOk());
    }
}
