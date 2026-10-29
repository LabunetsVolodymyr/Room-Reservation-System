package com.example.booking.common;

/**
 * Власний виняток "сутність не знайдена".
 * Наслідує RuntimeException (unchecked), тому не треба оголошувати throws у кожному методі,
 * а Spring автоматично відкочує транзакцію при unchecked-винятку.
 * Для REST перетворюється на HTTP 404 у GlobalExceptionHandler.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message); // текст повідомлення потрапить у поле "detail" відповіді
    }
}
