package com.example.booking.room;

import jakarta.validation.constraints.Min;      // мінімальне значення числа
import jakarta.validation.constraints.NotBlank; // рядок не null і не складається лише з пробілів
import jakarta.validation.constraints.Size;     // обмеження довжини

import java.time.LocalDateTime;

/**
 * DTO (Data Transfer Object) - об'єкт, яким обмінюється API з клієнтом. Не віддаємо Entity напряму, бо:
 * 1) не хочемо розкривати внутрішню структуру БД; 2) уникаємо проблем з lazy-зв'язками при серіалізації;
 * 3) можна мати різні "вигляди" одних даних.
 *
 * record (Java 16+) - незмінний клас: автоматично є конструктор, геттери виду name(), equals/hashCode/toString.
 * Анотації валідації спрацьовують, коли в контролері стоїть @Valid.
 */
public record RoomDto(
        Long id,                                    // у запиті ігнорується, у відповіді - id кімнати
        @NotBlank @Size(max = 100) String name,     // обов'язкова назва до 100 символів
        @Min(1) int capacity,                       // місткість щонайменше 1
        @Size(max = 500) String description,        // необов'язковий опис до 500 символів
        Boolean active,                             // Boolean (обгортка): null означає "не змінювати/значення за замовчуванням"
        LocalDateTime createdAt) {                  // лише для відповіді
}
