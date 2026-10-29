package com.example.booking.room;

import jakarta.persistence.*;   // JPA-анотації (Entity, Table, Id, Column...)
import lombok.Getter;           // Lombok генерує getX() на етапі компіляції
import lombok.Setter;           // Lombok генерує setX()

import java.time.LocalDateTime; // дата й час без часового поясу

/**
 * Entity (сутність) - Java-клас, відображений на таблицю БД. Hibernate (реалізація JPA) читає анотації
 * і генерує SQL. Схему таблиці створює НЕ Hibernate, а Liquibase (ddl-auto: validate лише перевіряє збіг).
 */
@Entity                     // клас є JPA-сутністю
@Table(name = "rooms")      // таблиця "rooms" (створена в Liquibase changeSet 001)
@Getter                     // Lombok: гетери для всіх полів
@Setter                     // Lombok: сетери для всіх полів
public class Room {

    @Id                                                     // первинний ключ
    @GeneratedValue(strategy = GenerationType.IDENTITY)     // значення генерує БД (autoIncrement/IDENTITY у Postgres)
    private Long id;

    // nullable=false -> NOT NULL; unique=true -> UNIQUE (дублюється в Liquibase); length - довжина VARCHAR
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false)
    private int capacity;           // примітив int: не може бути null

    @Column(length = 500)
    private String description;     // необов'язкове поле (NULL дозволено)

    @Column(nullable = false)
    private boolean active = true;  // значення за замовчуванням true для нових кімнат

    // updatable=false: після створення значення не змінюється навіть при UPDATE
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Callback життєвого циклу JPA: викликається безпосередньо перед першим INSERT
    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
