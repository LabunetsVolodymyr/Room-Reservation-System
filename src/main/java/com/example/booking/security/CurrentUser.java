package com.example.booking.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Єдина точка доступу до поточного користувача: однаково працює для JWT (API) та OIDC (UI).
 *
 * Теорія: після успішної автентифікації Spring Security кладе об'єкт Authentication у SecurityContext,
 * який зберігається в ThreadLocal (свій для кожного запиту/потоку). Доступ: SecurityContextHolder.
 *  - API (JWT): principal = Jwt (розібраний токен);
 *  - UI (OIDC): principal = OidcUser (користувач, що ввійшов через Keycloak).
 * Цей клас ховає різницю, тож сервіси не залежать від способу входу.
 */
@Component
public class CurrentUser {

    // Повертає "sub" - унікальний id користувача в Keycloak
    public String id() {
        Object principal = authentication().getPrincipal();
        if (principal instanceof Jwt jwt) {          // pattern matching: перевірка типу + приведення в одному рядку
            return jwt.getSubject();                 // claim "sub" із JWT
        }
        if (principal instanceof OidcUser user) {
            return user.getSubject();                // claim "sub" з ID-токена
        }
        throw new IllegalStateException("Unsupported principal: " + principal);
    }

    // true, якщо користувач має роль ADMIN або MANAGER (бачить усі бронювання, може підтверджувати)
    public boolean isPrivileged() {
        return authentication().getAuthorities().stream()
                .map(a -> a.getAuthority())          // GrantedAuthority -> рядок, наприклад "ROLE_ADMIN"
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_MANAGER"));
    }

    // Поточна автентифікація з потокового контексту безпеки
    private Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
