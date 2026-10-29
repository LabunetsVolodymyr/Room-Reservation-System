package com.example.booking.security;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit-тести перетворення ролей Keycloak (realm_access.roles) на GrantedAuthority з префіксом ROLE_. */
class JwtRoleConverterTest {

    private final JwtRoleConverter converter = new JwtRoleConverter();

    private Map<String, Object> claimsWithRoles(Object... roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("realm_access", Map.of("roles", List.of(roles)));
        return claims;
    }

    // ================= extractRoles(Map) =================
    @Test
    void extractRoles_claimsWithRoles_addsRolePrefix() {
        // Arrange
        Map<String, Object> claims = claimsWithRoles("ADMIN", "USER");

        // Act
        Collection<GrantedAuthority> result = JwtRoleConverter.extractRoles(claims);

        // Assert
        assertThat(result).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void extractRoles_noRealmAccessClaim_returnsEmpty() {
        // Arrange
        Map<String, Object> claims = Map.of("sub", "abc");

        // Act + Assert
        assertThat(JwtRoleConverter.extractRoles(claims)).isEmpty();
    }

    @Test
    void extractRoles_realmAccessWithoutRoles_returnsEmpty() {
        // Arrange
        Map<String, Object> claims = new HashMap<>();
        claims.put("realm_access", Map.of("other", "value"));

        // Act + Assert
        assertThat(JwtRoleConverter.extractRoles(claims)).isEmpty();
    }

    @Test
    void extractRoles_realmAccessNotAMap_returnsEmpty() {
        // Arrange: claim є, але має неправильний тип
        Map<String, Object> claims = new HashMap<>();
        claims.put("realm_access", "oops");

        // Act + Assert
        assertThat(JwtRoleConverter.extractRoles(claims)).isEmpty();
    }

    @Test
    void extractRoles_emptyRolesList_returnsEmpty() {
        // Arrange: межове значення - порожній список ролей
        Map<String, Object> claims = claimsWithRoles();

        // Act + Assert
        assertThat(JwtRoleConverter.extractRoles(claims)).isEmpty();
    }

    // ================= extractRoles(String accessToken) =================
    @Test
    void extractRoles_validAccessToken_readsRolesFromPayload() throws Exception {
        // Arrange: непідписаний JWT, цього достатньо для розбору payload
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("abc")
                .claim("realm_access", Map.of("roles", List.of("MANAGER")))
                .build();
        String token = new PlainJWT(claims).serialize();

        // Act
        Collection<GrantedAuthority> result = JwtRoleConverter.extractRoles(token);

        // Assert
        assertThat(result).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_MANAGER");
    }

    @Test
    void extractRoles_malformedAccessToken_returnsEmpty() {
        // Act
        Collection<GrantedAuthority> result = JwtRoleConverter.extractRoles("not-a-jwt");

        // Assert: резервний варіант - порожній список замість винятку
        assertThat(result).isEmpty();
    }

    // ================= convert(Jwt) =================
    @Test
    void convert_jwtWithRoles_setsUsernameAndAuthorities() {
        // Arrange
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("preferred_username", "admin")
                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                .build();

        // Act
        AbstractAuthenticationToken result = converter.convert(jwt);

        // Assert
        assertThat(result.getName()).isEqualTo("admin");
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }

    @Test
    void convert_jwtWithoutRoles_hasNoAuthorities() {
        // Arrange
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("preferred_username", "guest")
                .build();

        // Act
        AbstractAuthenticationToken result = converter.convert(jwt);

        // Assert
        assertThat(result.getName()).isEqualTo("guest");
        assertThat(result.getAuthorities()).isEmpty();
    }
}
