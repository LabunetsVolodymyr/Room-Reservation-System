package com.example.booking.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * тип 3 - (резорвер) анотація для параметра методу контролера, яку резолвить HandlerMethodArgumentResolver.
 *
 * Приклад:
 *     @GetMapping("/mine")
 *     public List<BookingDto> mine(@CurrentUserId String userId) { ... }
 *
 * Spring MVC перед викликом методу питає всі зареєстровані резолвери "чи підтримуєш ти цей параметр?".
 * CurrentUserIdArgumentResolver відповідає "так" для String-параметрів з цією анотацією і підставляє "sub"
 * поточного автентифікованого користувача (з JWT або OIDC) - без повторення логіки видобування в кожному методі.
 *
 * @Target(PARAMETER) - анотацію можна ставити лише на параметри; @Retention(RUNTIME) - резолвер бачить її через reflection.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
