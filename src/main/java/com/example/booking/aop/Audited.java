package com.example.booking.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * аспект 1 - журнал аудиту (хто, що і з чим зробив).
 *
 * Метод, позначений @Audited("BOOKING_CONFIRMED"), після УСПІШНОГО виконання потрапляє в журнал аудиту:
 * час, користувач (sub), дія, метод, id об'єкта (перший аргумент) і результат.
 * Обробляє AuditAspect (@AfterReturning): якщо метод кинув виняток (403, 404, 409), запис не створюється.
 *
 * @Target(METHOD) - лише на методи; @Retention(RUNTIME) - pointcut @annotation(...) бачить її через reflection.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** Назва дії в журналі, наприклад "BOOKING_CONFIRMED". */
    String value();
}
