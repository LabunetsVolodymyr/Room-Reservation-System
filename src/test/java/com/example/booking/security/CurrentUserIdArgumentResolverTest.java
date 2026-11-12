package com.example.booking.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Лаба 3, тип 3 (AC6-AC7): Unit-тести резолвера без Spring-контексту.
 * MethodParameter беремо з тестового "контролера" Sample, запит - MockHttpServletRequest з потрібним principal.
 */
class CurrentUserIdArgumentResolverTest {

    private final CurrentUserIdArgumentResolver resolver = new CurrentUserIdArgumentResolver();

    /** Сигнатури, на яких перевіряємо supportsParameter. */
    @SuppressWarnings("unused")
    static class Sample {
        void handle(@CurrentUserId String userId, String plain, @CurrentUserId Long wrongType) {
        }
    }

    private static MethodParameter parameter(int index) throws NoSuchMethodException {
        Method method = Sample.class.getDeclaredMethod("handle", String.class, String.class, Long.class);
        return new MethodParameter(method, index);
    }

    private static ServletWebRequest requestWithPrincipal(java.security.Principal principal) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bookings/mine");
        request.setUserPrincipal(principal);
        return new ServletWebRequest(request);
    }

    // ================= AC6: supportsParameter =================
    @Test
    void supportsParameter_stringParameterWithAnnotation_returnsTrue() throws Exception {
        // Arrange
        MethodParameter annotated = parameter(0);

        // Act
        boolean supported = resolver.supportsParameter(annotated);

        // Assert
        assertThat(supported).isTrue();
    }

    @Test
    void supportsParameter_parameterWithoutAnnotation_returnsFalse() throws Exception {
        // Arrange
        MethodParameter plain = parameter(1);

        // Act
        boolean supported = resolver.supportsParameter(plain);

        // Assert
        assertThat(supported).isFalse();
    }

    @Test
    void supportsParameter_annotatedParameterOfWrongType_returnsFalse() throws Exception {
        // Arrange: анотація є, але тип Long - "sub" є рядком, тому такий параметр не підтримуємо
        MethodParameter wrongType = parameter(2);

        // Act
        boolean supported = resolver.supportsParameter(wrongType);

        // Assert
        assertThat(supported).isFalse();
    }

    // ================= AC7: resolveArgument =================
    @Test
    void resolveArgument_jwtAuthenticatedRequest_returnsSubjectClaim() throws Exception {
        // Arrange
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-1").build();
        ServletWebRequest request = requestWithPrincipal(new JwtAuthenticationToken(jwt));

        // Act
        Object userId = resolver.resolveArgument(parameter(0), null, request, null);

        // Assert
        assertThat(userId).isInstanceOf(String.class).isEqualTo("user-1");
    }

    @Test
    void resolveArgument_oidcAuthenticatedRequest_returnsSubjectFromIdToken() throws Exception {
        // Arrange
        OidcIdToken idToken = new OidcIdToken("token",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T01:00:00Z"),
                Map.<String, Object>of("sub", "oidc-sub-1"));
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        DefaultOidcUser user = new DefaultOidcUser(authorities, idToken);
        ServletWebRequest request = requestWithPrincipal(new OAuth2AuthenticationToken(user, authorities, "keycloak"));

        // Act
        Object userId = resolver.resolveArgument(parameter(0), null, request, null);

        // Assert
        assertThat(userId).isEqualTo("oidc-sub-1");
    }

    @Test
    void resolveArgument_anonymousRequest_throwsIllegalState() throws Exception {
        // Arrange: principal відсутній
        ServletWebRequest request = requestWithPrincipal(null);
        MethodParameter annotated = parameter(0);

        // Act
        Throwable thrown = catchThrowable(() -> resolver.resolveArgument(annotated, null, request, null));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires an authenticated user");
    }

    @Test
    void resolveArgument_unsupportedPrincipalType_throwsIllegalState() throws Exception {
        // Arrange: Authentication є, але його principal - звичайний рядок (не Jwt і не OidcUser)
        ServletWebRequest request = requestWithPrincipal(new TestingAuthenticationToken("plain", "pw"));
        MethodParameter annotated = parameter(0);

        // Act
        Throwable thrown = catchThrowable(() -> resolver.resolveArgument(annotated, null, request, null));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported principal");
    }
}
