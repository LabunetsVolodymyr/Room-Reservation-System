package com.example.booking.security;

import com.nimbusds.jwt.JWTParser;                                   // бібліотека Nimbus: розбір JWT
import org.springframework.core.convert.converter.Converter;         // загальний інтерфейс "перетворювач" Spring
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;           // "право/роль" користувача
import org.springframework.security.core.authority.SimpleGrantedAuthority; // проста реалізація GrantedAuthority (рядок)
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Keycloak кладе realm-ролі в claim realm_access.roles. Spring за замовчуванням читає scope/scp,
 * тому перетворюємо ролі на GrantedAuthority з префіксом ROLE_ (щоб працювали hasRole / hasAnyRole).
 *
 * Приклад payload токена Keycloak:
 *   { "sub": "a1b2...", "preferred_username": "admin",
 *     "realm_access": { "roles": ["ADMIN", "USER", "offline_access"] } }
 * Після конвертації: [ROLE_ADMIN, ROLE_USER, ROLE_offline_access].
 *
 * Клас реалізує Converter<Jwt, AbstractAuthenticationToken>: Spring Security викликає convert(jwt) ПІСЛЯ того,
 * як підпис і термін дії токена вже перевірені.
 */
@Component
public class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Створюємо Authentication: сам токен + список ролей + "ім'я" користувача (логін)
        return new JwtAuthenticationToken(jwt, extractRoles(jwt.getClaims()),
                jwt.getClaimAsString("preferred_username"));
    }

    // Витягує ролі з набору claims (Map). Використовується і для JWT (API), і для access token (UI).
    public static Collection<GrantedAuthority> extractRoles(Map<String, Object> claims) {
        // Pattern matching: перевіряємо, що realm_access - це Map, а roles у ньому - колекція
        if (claims.get("realm_access") instanceof Map<?, ?> realmAccess
                && realmAccess.get("roles") instanceof Collection<?> roles) {
            return roles.stream()
                    // Кожну роль перетворюємо на authority з префіксом ROLE_ (стандарт для hasRole)
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
        }
        return List.of(); // ролей немає - порожній список
    }

    /** Для OIDC-входу: ролі беремо з access token (його видав Keycloak напряму застосунку). */
    // Підпис тут не перевіряємо: токен ми щойно отримали безпосередньо від Keycloak по захищеному каналу
    // (обмін authorization code на токени), а не від клієнта.
    public static Collection<GrantedAuthority> extractRoles(String accessToken) {
        try {
            // JWT = три частини base64 через крапку; парсимо payload і беремо claims
            return extractRoles(JWTParser.parse(accessToken).getJWTClaimsSet().getClaims());
        } catch (ParseException e) {
            return List.of(); // токен не розібрався - вважаємо, що ролей немає
        }
    }
}
