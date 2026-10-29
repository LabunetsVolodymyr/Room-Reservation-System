package com.example.booking.booking;

import com.example.booking.room.Room;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit-тести згенерованого MapStruct-мапера бронювань (без Spring). */
class BookingMapperTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 12, 1, 10, 0);
    private static final LocalDateTime END = START.plusHours(1);

    private final BookingMapper mapper = Mappers.getMapper(BookingMapper.class);

    private Booking booking() {
        Room room = new Room();
        room.setId(3L);
        room.setName("Beta");
        Booking booking = new Booking();
        booking.setId(10L);
        booking.setRoom(room);
        booking.setOwnerId("user-1");
        booking.setStartTime(START);
        booking.setEndTime(END);
        booking.setStatus(BookingStatus.CONFIRMED);
        return booking;
    }

    @Test
    void toDto_validBooking_flattensRoomIdAndName() {
        // Act
        BookingDto dto = mapper.toDto(booking());

        // Assert
        assertThat(dto.id()).isEqualTo(10L);
        assertThat(dto.roomId()).isEqualTo(3L);
        assertThat(dto.roomName()).isEqualTo("Beta");
        assertThat(dto.ownerId()).isEqualTo("user-1");
        assertThat(dto.status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void toDto_nullBooking_returnsNull() {
        assertThat(mapper.toDto(null)).isNull();
    }

    @Test
    void toDtoList_twoBookings_mapsEachElement() {
        assertThat(mapper.toDtoList(List.of(booking(), booking()))).hasSize(2);
    }

    @Test
    void toEntity_dtoWithServerManagedFields_ignoresThem() {
        // Arrange
        BookingDto dto = new BookingDto(5L, 3L, "Beta", "hacker", START, END, BookingStatus.CONFIRMED, START);

        // Act
        Booking entity = mapper.toEntity(dto);

        // Assert: ці поля виставляє сервіс, а не клієнт
        assertThat(entity.getId()).isNull();
        assertThat(entity.getRoom()).isNull();
        assertThat(entity.getOwnerId()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getStatus()).isEqualTo(BookingStatus.PENDING); // значення за замовчуванням сутності
        assertThat(entity.getStartTime()).isEqualTo(START);
        assertThat(entity.getEndTime()).isEqualTo(END);
    }

    @Test
    void update_newTimes_changesOnlyStartAndEnd() {
        // Arrange
        Booking existing = booking();
        LocalDateTime newStart = START.plusDays(1);
        BookingDto dto = new BookingDto(null, 99L, null, "hacker", newStart, newStart.plusHours(2),
                BookingStatus.CANCELLED, null);

        // Act
        mapper.update(dto, existing);

        // Assert
        assertThat(existing.getStartTime()).isEqualTo(newStart);
        assertThat(existing.getEndTime()).isEqualTo(newStart.plusHours(2));
        assertThat(existing.getOwnerId()).isEqualTo("user-1");
        assertThat(existing.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(existing.getRoom().getId()).isEqualTo(3L);
    }
}
