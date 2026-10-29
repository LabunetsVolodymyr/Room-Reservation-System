package com.example.booking.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;                 // порядок вибору ланцюжка фільтрів
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity; // білдер правил безпеки
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;

import java.util.HashSet;
import java.util.Set;

/**
 * Центральна конфігурація безпеки. Тут два ПАРАЛЕЛЬНІ способи автентифікації:
 *   1) apiChain - /api/**: JWT, без сесії (для curl/Postman/інших сервісів);
 *   2) uiChain  - решта (веб-UI): OIDC-логін через Keycloak, сесія в cookie.
 *
 * Теорія: Spring Security - це ланцюжок сервлет-фільтрів (SecurityFilterChain). Для кожного запиту обирається
 * ПЕРШИЙ ланцюжок (за @Order), чий securityMatcher підходить до URL. Усередині ланцюжка фільтри
 * перевіряють автентифікацію (хто ти?) і авторизацію (що тобі дозволено?).
 */
@Configuration                 // клас з @Bean-методами
@EnableMethodSecurity          // вмикає @PreAuthorize/@PostAuthorize на методах (проксі навколо бінів)
public class SecurityConfig {

    /**
     * Ланцюжок 1: REST API. Stateless, автентифікація через JWT (Authorization: Bearer ...).
     * Рівень 1 обмеження доступу: правила в SecurityFilterChain.
     */
    @Bean
    @Order(1) // перевіряється першим: якщо URL починається з /api/ - працює саме цей ланцюжок
    SecurityFilterChain apiChain(HttpSecurity http, JwtRoleConverter jwtRoleConverter) throws Exception {
        return http
                // Цей ланцюжок застосовується ЛИШЕ до шляхів /api/**
                .securityMatcher("/api/**")
                // Правила авторизації: перевіряються ЗВЕРХУ ВНИЗ, перше підходяще виграє
                .authorizeHttpRequests(auth -> auth
                        // створювати кімнати можуть ADMIN і MANAGER (hasAnyRole сам додає префікс ROLE_)
                        .requestMatchers(HttpMethod.POST, "/api/rooms/**").hasAnyRole("ADMIN", "MANAGER")
                        // оновлювати кімнати - ADMIN і MANAGER
                        .requestMatchers(HttpMethod.PUT, "/api/rooms/**").hasAnyRole("ADMIN", "MANAGER")
                        // видаляти кімнати - лише ADMIN. Без ролі -> 403 Forbidden (критерій 6)
                        .requestMatchers(HttpMethod.DELETE, "/api/rooms/**").hasRole("ADMIN")
                        // усе інше - достатньо бути автентифікованим (дійсний токен). Без токена -> 401
                        .anyRequest().authenticated())
                // Resource Server: читає заголовок "Authorization: Bearer <token>", перевіряє підпис (ключі
                // з Keycloak за issuer-uri), термін дії та issuer; наш конвертер додає ролі
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtRoleConverter)))
                // STATELESS: сесію не створюємо і не використовуємо - кожен запит несе власний токен
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF-атака використовує cookie, які браузер надсилає автоматично. Токен у заголовку так не
                // надсилається, і сесії немає, тому для API захист CSRF не потрібен
                .csrf(AbstractHttpConfigurer::disable)   // немає сесії/cookie - немає CSRF-ризику
                .build();
    }

    /**
     * Ланцюжок 2: UI. OIDC Authorization Code Flow (редирект на Keycloak), сесія в cookie.
     */
    @Bean
    @Order(2) // другий: securityMatcher не задано, тож ловить усе, що не підійшло першому ланцюжку
    SecurityFilterChain uiChain(HttpSecurity http,
                                OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService,
                                ClientRegistrationRepository clientRegistrations) throws Exception {
        // Обробник виходу: окрім завершення локальної сесії, перенаправляє на endpoint виходу Keycloak
        // (RP-Initiated Logout), тому користувач виходить і з Keycloak
        var logoutHandler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrations);
        // Куди повернути користувача після виходу ({baseUrl} Spring підставить сам: http://localhost:8080)
        logoutHandler.setPostLogoutRedirectUri("{baseUrl}/");

        return http
                .authorizeHttpRequests(auth -> auth
                        // сторінка помилки доступна всім (інакше можливе зациклення редиректів на логін)
                        .requestMatchers("/error").permitAll()
                        // /admin/** - лише ADMIN (інші отримують 403). Це правило рівня SecurityFilterChain
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // решта сторінок - лише для автентифікованих; неавтентифікованих перенаправляє на логін
                        .anyRequest().authenticated())
                // OIDC-логін (Authorization Code Flow): редирект на Keycloak -> повернення з кодом на
                // /login/oauth2/code/keycloak -> обмін коду на токени -> користувач у сесії
                .oauth2Login(oauth -> oauth.userInfoEndpoint(u -> u.oidcUserService(oidcUserService)))
                // Вихід: POST /logout (з CSRF-токеном) -> наш обробник
                .logout(logout -> logout.logoutSuccessHandler(logoutHandler))
                // CSRF для UI залишається УВІМКНЕНИМ (за замовчуванням): форми Thymeleaf додають токен самі
                .build();
    }

    /** Додає ролі Keycloak (з access token) до користувача, який увійшов через OIDC. */
    // Стандартний OidcUserService будує authorities зі scope-ів (SCOPE_openid тощо), а не з ролей Keycloak.
    // Тому викликаємо його і додаємо ролі, розібрані з access token.
    @Bean
    OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        OidcUserService delegate = new OidcUserService(); // стандартна реалізація, яку розширюємо
        return request -> {
            OidcUser user = delegate.loadUser(request);   // стандартний користувач (дані з ID-токена)
            Set<GrantedAuthority> authorities = new HashSet<>(user.getAuthorities()); // копія стандартних прав
            // додаємо ROLE_* з access token (realm_access.roles)
            authorities.addAll(JwtRoleConverter.extractRoles(request.getAccessToken().getTokenValue()));
            // Новий OidcUser: наші authorities + ID-токен + userinfo; "ім'я" = preferred_username (логін)
            return new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo(), "preferred_username");
        };
    }
}
