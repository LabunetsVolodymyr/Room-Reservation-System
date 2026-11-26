package com.example.booking.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * аспект 2 - обмеження частоти викликів (rate limiting) для кожного користувача окремо.
 *
 * @RateLimited(maxCalls = 5, perSeconds = 60) на методі означає: один користувач може викликати його
 * не більше 5 разів за 60 секунд. Шостий виклик у цьому вікні блокується винятком RateLimitExceededException
 * (HTTP 429 Too Many Requests). Захищає від спаму бронюваннями.
 * Обробляє RateLimitAspect (@Before).
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    /** Скільки викликів дозволено у вікні. */
    int maxCalls();

    /** Довжина вікна в секундах. */
    int perSeconds();
}
