package com.example.booking.booking;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * DTO бронювання. Замість вкладеного об'єкта Room віддаємо плоскі roomId і roomName - простіше для клієнта.
 * При створенні клієнт надсилає лише roomId, startTime, endTime; решту полів сервер заповнює сам
 * (ownerId беремо з токена, status = PENDING), тому довіряти цим полям із запиту не можна.
 */
public record BookingDto(
        Long id,                            // лише у відповіді
        @NotNull Long roomId,               // обов'язково: яку кімнату бронюємо
        String roomName,                    // лише у відповіді (зручно для UI)
        String ownerId,                     // лише у відповіді: sub власника
        @NotNull LocalDateTime startTime,   // обов'язково; формат JSON: "2026-12-01T10:00:00"
        @NotNull LocalDateTime endTime,     // обов'язково
        BookingStatus status,               // лише у відповіді
        LocalDateTime createdAt) {          // лише у відповіді
}
