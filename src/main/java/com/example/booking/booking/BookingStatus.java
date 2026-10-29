package com.example.booking.booking;

/**
 * Статуси життєвого циклу бронювання.
 * PENDING   - створено, чекає підтвердження менеджера;
 * CONFIRMED - підтверджено (ADMIN/MANAGER);
 * CANCELLED - скасовано.
 * У БД зберігається як текст (@Enumerated(STRING) у Booking), а не як номер.
 */
public enum BookingStatus {
    PENDING, CONFIRMED, CANCELLED
}
