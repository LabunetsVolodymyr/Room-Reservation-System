package com.example.booking.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-тести CurrentUser. Залежностей немає, тому мокати нічого: кладемо готовий Authentication у
 * SecurityContextHolder. Після кожного тесту контекст очищається, щоб тести не залежали від порядку виконання.
 */
class CurrentUserTest {

    private final CurrentUser currentUser = new CurrentUser();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // ================= id() =================
    @Test
    void id_jwtPrincipal_returnsSubjectClaim() {
        // Arrange
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("jwt-sub-1").build();
        authenticate(new JwtAuthenticationToken(jwt));

        // Act
        String id = currentUser.id();

        // Assert
        assertThat(id).isEqualTo("jwt-sub-1");
    }

    @Test
    void id_oidcPrincipal_returnsSubjectClaim() {
        // Arrange
        OidcIdToken idToken = new OidcIdToken("token",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T01:00:00Z"),
                Map.<String, Object>of("sub", "oidc-sub-1"));
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        DefaultOidcUser user = new DefaultOidcUser(authorities, idToken);
        authenticate(new OAuth2AuthenticationToken(user, authorities, "keycloak"));

        // Act
        String id = currentUser.id();

        // Assert
        assertThat(id).isEqualTo("oidc-sub-1");
    }

    @Test
    void id_unsupportedPrincipal_throwsIllegalState() {
        // Arrange: principal - звичайний рядок, а не Jwt чи OidcUser
        authenticate(new TestingAuthenticationToken("plain-string", "pw", "ROLE_USER"));

        // Act + Assert
        assertThatThrownBy(currentUser::id)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported principal");
    }

    // ================= isPrivileged() =================
    @ParameterizedTest
    @CsvSource({
            "ROLE_ADMIN, true",
            "ROLE_MANAGER, true",
            "ROLE_USER, false",
            "ROLE_offline_access, false"
    })
    void isPrivileged_singleRole_returnsExpectedFlag(String role, boolean expected) {
        // Arrange
        authenticate(new TestingAuthenticationToken("someone", "pw", role));

        // Act
        boolean privileged = currentUser.isPrivileged();

        // Assert
        assertThat(privileged).isEqualTo(expected);
    }

    @Test
    void isPrivileged_userWithAdminAmongOtherRoles_returnsTrue() {
        // Arrange
        authenticate(new TestingAuthenticationToken("someone", "pw", "ROLE_USER", "ROLE_ADMIN"));

        // Act + Assert
        assertThat(currentUser.isPrivileged()).isTrue();
    }

    @Test
    void isPrivileged_noAuthorities_returnsFalse() {
        // Arrange: межовий випадок - порожній набір ролей
        authenticate(new TestingAuthenticationToken("someone", "pw"));

        // Act + Assert
        assertThat(currentUser.isPrivileged()).isFalse();
    }
}
