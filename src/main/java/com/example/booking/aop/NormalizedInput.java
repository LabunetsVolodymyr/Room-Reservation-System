package com.example.booking.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * аспект 3 - нормалізація текстового вводу.
 *
 * Перед викликом методу всі рядки в аргументах (самі String-аргументи і String-поля record-DTO)
 * очищуються: пробіли на краях прибираються, кілька пробілів поспіль стають одним.
 * Наприклад, "  Room   Alpha " -> "Room Alpha". Тоді "Alpha" і "Alpha " не стануть двома різними кімнатами.
 *
 * Обробляє InputNormalizationAspect (@Around): він підміняє аргументи через proceed(newArgs).
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NormalizedInput {
}
