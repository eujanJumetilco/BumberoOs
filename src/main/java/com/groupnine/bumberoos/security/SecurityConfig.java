package com.groupnine.bumberoos.security;

import com.groupnine.bumberoos.domain.enums.UserRole;
import com.groupnine.bumberoos.repositories.UserAccountRepository;
import com.groupnine.bumberoos.services.UserAccountService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private static final String UNAUTHORIZED_BODY = "{\"error\":\"please sign in\"}";
    private static final String FORBIDDEN_BODY = "{\"error\":\"administrator access required\"}";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, UserAccountRepository accounts, UserAccountService accountService
    ) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        // PINAYAGAN NA NATIN LAHAT NG MODULES
                        .requestMatchers("/api/auth/registered", "/api/auth/signin", "/api/events/**", "/api/staff/**", "/api/items/**", "/api/incidents/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup").access(signupAllowed(accountService))
                        .requestMatchers("/api/accounts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/settings/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) -> json(response, 401, UNAUTHORIZED_BODY))
                        .accessDeniedHandler((request, response, e) -> json(response, 403, FORBIDDEN_BODY)))
                .addFilterBefore(new AccountRefreshFilter(accounts), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static AuthorizationManager<RequestAuthorizationContext> signupAllowed(UserAccountService accounts) {
        return (authentication, context) -> new AuthorizationDecision(
                !accounts.hasEnabledAdmin() || isAdmin(authentication.get()));
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + UserRole.ADMIN.name()));
    }

    private static void json(jakarta.servlet.http.HttpServletResponse response, int status, String body)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(body);
    }
}