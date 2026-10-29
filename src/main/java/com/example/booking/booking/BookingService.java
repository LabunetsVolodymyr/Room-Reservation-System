package com.example.booking.booking;

import com.example.booking.common.NotFoundException;
import com.example.booking.room.Room;
import com.example.booking.room.RoomRepository;
import com.example.booking.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;     // "доступ заборонено" -> 403
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Бізнес-логіка бронювань і правила доступу на рівні даних ("чиє це бронювання").
 * Інші анотації класу (@Service, @RequiredArgsConstructor, @Transactional(readOnly)) - як у RoomService.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingService {

    private final BookingRepository repository;
    private final RoomRepository roomRepository;
    private final BookingMapper mapper;
    private final CurrentUser currentUser; // хто зараз виконує запит (однаково для JWT і OIDC)

    /** USER бачить лише свої бронювання, ADMIN/MANAGER - усі. */
    public List<BookingDto> list() {
        // Тернарний оператор: привілейований -> усі записи, інакше -> лише з owner_id == sub поточного користувача
        List<Booking> bookings = currentUser.isPrivileged()
                ? repository.findAllByOrderByStartTimeDesc()
                : repository.findByOwnerIdOrderByStartTimeDesc(currentUser.id());
        return mapper.toDtoList(bookings);
    }

    public BookingDto get(Long id) {
        return mapper.toDto(loadAccessible(id)); // loadAccessible перевіряє, що бронювання доступне цьому користувачу
    }

    @Transactional
    public BookingDto create(BookingDto dto) {
        validateTimes(dto);                              // endTime має бути після startTime
        Booking booking = mapper.toEntity(dto);          // беремо з DTO лише start/end
        booking.setRoom(activeRoom(dto.roomId()));       // завантажуємо кімнату й перевіряємо, що вона активна
        booking.setOwnerId(currentUser.id());            // власник = sub з токена (не з тіла запиту!)
        booking.setStatus(BookingStatus.PENDING);        // новий запис завжди PENDING
        return mapper.toDto(repository.save(booking));   // INSERT
    }

    @Transactional
    public BookingDto update(Long id, BookingDto dto) {
        Booking booking = loadAccessible(id);            // чужі бронювання (для USER) -> 403
        if (booking.getStatus() != BookingStatus.PENDING) {
            // Підтверджене або скасоване бронювання змінювати не можна -> 409 Conflict
            throw new IllegalStateException("Only PENDING bookings can be edited");
        }
        validateTimes(dto);
        mapper.update(dto, booking);                     // оновлює start/end
        booking.setRoom(activeRoom(dto.roomId()));       // можна змінити кімнату
        return mapper.toDto(repository.save(booking));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(loadAccessible(id));
    }

    /** Другий рівень захисту: фільтр пропускає будь-якого автентифікованого, а метод - лише ADMIN/MANAGER. */
    // ВАЖЛИВО для критерію 7: у SecurityFilterChain для /api/bookings/{id}/confirm немає ролевого правила.
    // Запит від USER проходить фільтр, але проксі @PreAuthorize кидає AccessDeniedException ДО входу в метод.
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public BookingDto confirm(Long id) {
        Booking booking = loadAccessible(id);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Only PENDING bookings can be confirmed");
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        // Окремо save() не потрібен: сутність "керована" Hibernate, зміни зафіксуються при коміті (dirty checking)
        return mapper.toDto(booking);
    }

    @Transactional
    public BookingDto cancel(Long id) {
        Booking booking = loadAccessible(id);
        booking.setStatus(BookingStatus.CANCELLED);
        return mapper.toDto(booking);
    }

    // Шукає бронювання і перевіряє право доступу: привілейовані бачать усе, USER - лише своє
    private Booking loadAccessible(Long id) {
        Booking booking = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking " + id + " not found")); // 404
        if (!currentUser.isPrivileged() && !booking.getOwnerId().equals(currentUser.id())) {
            throw new AccessDeniedException("Not your booking"); // 403: Spring Security перетворить на відповідь
        }
        return booking;
    }

    // Знаходить кімнату за id і перевіряє, що вона активна
    private Room activeRoom(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room " + roomId + " not found"));
        if (!room.isActive()) {
            throw new IllegalArgumentException("Room is not active"); // 400
        }
        return room;
    }

    // Проста бізнес-перевірка часового інтервалу
    private void validateTimes(BookingDto dto) {
        if (!dto.endTime().isAfter(dto.startTime())) {
            throw new IllegalArgumentException("endTime must be after startTime"); // 400
        }
    }
}
