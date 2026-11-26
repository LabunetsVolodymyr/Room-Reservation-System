package com.example.booking.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;

/**
 * Аспект для @NormalizedInput. Advice-тип @Around: охоплює весь виклик і сам вирішує, з ЯКИМИ аргументами
 * викликати метод - proceed(newArgs). Цього не вміє жоден інший тип advice.
 *
 * Record-DTO незмінні (немає сеттерів), тому новий екземпляр будуємо через reflection: читаємо компоненти
 * record-а (getRecordComponents) і викликаємо його канонічний конструктор.
 */
@Aspect
@Component
public class InputNormalizationAspect {

    @Around("@annotation(com.example.booking.aop.NormalizedInput)")
    public Object normalizeArguments(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        Object[] normalized = new Object[args.length];
        boolean changed = false;
        for (int i = 0; i < args.length; i++) {
            normalized[i] = normalizeValue(args[i]);
            changed |= normalized[i] != args[i];
        }
        // Нічого не змінилось - звичайний proceed(); інакше метод отримає вже очищені аргументи
        return changed ? joinPoint.proceed(normalized) : joinPoint.proceed();
    }

    // Повертає ТОЙ САМИЙ об'єкт, якщо змінювати нічого (так легко визначити, чи були зміни)
    static Object normalizeValue(Object value) {
        if (value instanceof String text) {
            return normalizeText(text);
        }
        if (value != null && value.getClass().isRecord()) {
            return normalizeRecord(value);
        }
        return value;
    }

    // "  Room   Alpha " -> "Room Alpha"
    static String normalizeText(String text) {
        String normalized = text.strip().replaceAll("\\s+", " ");
        return normalized.equals(text) ? text : normalized;
    }

    // Новий record з нормалізованими String-компонентами (або той самий, якщо змінювати нічого)
    private static Object normalizeRecord(Object record) {
        RecordComponent[] components = record.getClass().getRecordComponents();
        Object[] values = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        boolean changed = false;
        try {
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                Object value = components[i].getAccessor().invoke(record);
                values[i] = value instanceof String text ? normalizeText(text) : value;
                changed |= values[i] != value;
            }
            if (!changed) {
                return record;
            }
            Constructor<?> canonical = record.getClass().getDeclaredConstructor(types);
            return canonical.newInstance(values);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot normalize " + record.getClass().getSimpleName(), e);
        }
    }
}
