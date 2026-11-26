package com.example.booking.aop;

import java.time.Instant;

/**
 * Один запис журналу аудиту.
 *
 * @param at      коли (час із Clock - у тестах фіксований)
 * @param actor   хто: sub користувача з Keycloak або "anonymous"
 * @param action  що: значення з @Audited, напр. "BOOKING_CONFIRMED"
 * @param method  який метод, напр. "BookingService.confirm(..)"
 * @param target  з чим: перший аргумент методу (id бронювання/кімнати) або null
 * @param outcome результат: напр. "status=CONFIRMED" або "OK" для void-методів
 */
public record AuditEvent(Instant at, String actor, String action, String method, Object target, String outcome) {
}
