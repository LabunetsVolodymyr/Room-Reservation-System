package com.example.booking.room;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository - шар доступу до даних. Достатньо оголосити інтерфейс: Spring Data JPA на старті
 * сам створює реалізацію (проксі) з готовими методами: save, findById, findAll, delete, saveAndFlush тощо.
 * Generics: <Room, Long> = <тип сутності, тип первинного ключа>.
 */
public interface RoomRepository extends JpaRepository<Room, Long> {
}
