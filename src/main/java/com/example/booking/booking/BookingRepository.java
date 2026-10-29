package com.example.booking.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA сам будує SQL із НАЗВИ методу (derived queries):
 *  findAllByOrderByStartTimeDesc  -> SELECT * FROM bookings ORDER BY start_time DESC
 *  findByOwnerIdOrderByStartTimeDesc(owner) -> ... WHERE owner_id = ? ORDER BY start_time DESC
 * Тому реалізації писати не потрібно.
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Усі бронювання, нові (за часом початку) першими. Використовується для ADMIN/MANAGER
    List<Booking> findAllByOrderByStartTimeDesc();

    // Лише бронювання конкретного власника. Використовується для звичайного USER
    List<Booking> findByOwnerIdOrderByStartTimeDesc(String ownerId);
}
