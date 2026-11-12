package com.example.booking.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.security.Principal;

/**
 * Резолвер для @CurrentUserId: видобуває "sub" користувача з КОНТЕКСТУ ЗАПИТУ.
 *
 * Звідки береться користувач: Spring Security обгортає HttpServletRequest, і request.getUserPrincipal()
 * повертає поточний Authentication (JwtAuthenticationToken для API, OAuth2AuthenticationToken для UI).
 * Його principal - Jwt або OidcUser, з якого береться claim "sub" (CurrentUser.subjectOf).
 *
 * Реєструється в WebMvcConfig.addArgumentResolvers(...).
 */
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    // Викликається Spring MVC для КОЖНОГО параметра методу контролера: true -> цей параметр резолвимо ми
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && String.class.equals(parameter.getParameterType());
    }

    // Викликається лише для параметрів, для яких supportsParameter повернув true. Результат стає аргументом методу.
    @Override
    public String resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Principal principal = webRequest.getUserPrincipal();
        if (principal instanceof Authentication authentication) {
            return CurrentUser.subjectOf(authentication.getPrincipal());
        }
        // Для /api/** сюди не дійдемо: неавтентифікований запит відсіче SecurityFilterChain (401)
        throw new IllegalStateException("@CurrentUserId requires an authenticated user, but request has: " + principal);
    }
}
