package com.example.booking.booking;

import com.example.booking.room.Room;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Entity бронювання: відображається на таблицю "bookings" (створена Liquibase changeSet 002). */
@Entity
@Table(name = "bookings")
@Getter
@Setter
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // id генерує БД
    private Long id;

    // Зв'язок "багато бронювань -> одна кімната". У БД це зовнішній ключ room_id -> rooms(id).
    // LAZY: кімната завантажується з БД лише коли до неї звернулися (економія запитів).
    // optional=false: бронювання без кімнати неможливе.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)  // назва колонки зовнішнього ключа
    private Room room;

    /** Поле "sub" з токена Keycloak. Користувачів у власній БД не зберігаємо. */
    // "sub" (subject) - унікальний незмінний ідентифікатор користувача в Keycloak (UUID).
    // Саме його, а не логін, зручно зберігати як власника: логін може змінитися, sub - ні.
    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;    // початок бронювання

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;      // кінець бронювання

    // EnumType.STRING зберігає "PENDING" текстом. За замовчуванням (ORDINAL) зберігається номер 0,1,2 -
    // це небезпечно: перестановка констант в enum зламає старі дані.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING; // новий запис починається зі статусу PENDING

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Виконується перед першим INSERT і виставляє час створення
    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
