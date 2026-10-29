package com.example.booking.booking;

import com.example.booking.common.NotFoundException;
import com.example.booking.room.Room;
import com.example.booking.room.RoomRepository;
import com.example.booking.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-тести BookingService. Без Spring-контексту: залежності (@Mock) імітуються Mockito,
 * @InjectMocks створює сервіс через конструктор. Мапер - справжній MapStruct-мапер (@Spy), без бази й мережі.
 * Час фіксований (START/END), поточний час не використовується.
 * Анотація @PreAuthorize у Unit-тесті НЕ діє (проксі створює лише Spring), тут перевіряється лише логіка методів.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final String USER_ID = "user-1";
    private static final String OTHER_ID = "user-2";
    private static final LocalDateTime START = LocalDateTime.of(2026, 12, 1, 10, 0);
    private static final LocalDateTime END = START.plusHours(1);

    @Mock
    private BookingRepository repository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private CurrentUser currentUser;
    @Spy
    private BookingMapper mapper = Mappers.getMapper(BookingMapper.class);

    @InjectMocks
    private BookingService service;

    // ---------- helpers ----------
    private Room room(Long id, boolean active) {
        Room room = new Room();
        room.setId(id);
        room.setName("Alpha");
        room.setCapacity(6);
        room.setActive(active);
        return room;
    }

    private Booking booking(Long id, String ownerId, BookingStatus status) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setRoom(room(1L, true));
        booking.setOwnerId(ownerId);
        booking.setStartTime(START);
        booking.setEndTime(END);
        booking.setStatus(status);
        return booking;
    }

    private BookingDto request(Long roomId, LocalDateTime start, LocalDateTime end) {
        return new BookingDto(null, roomId, null, null, start, end, null, null);
    }

    // ================= list =================
    @Test
    void list_regularUser_returnsOnlyOwnBookings() {
        // Arrange
        when(currentUser.isPrivileged()).thenReturn(false);
        when(currentUser.id()).thenReturn(USER_ID);
        when(repository.findByOwnerIdOrderByStartTimeDesc(USER_ID))
                .thenReturn(List.of(booking(1L, USER_ID, BookingStatus.PENDING)));

        // Act
        List<BookingDto> result = service.list();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).ownerId()).isEqualTo(USER_ID);
        verify(repository).findByOwnerIdOrderByStartTimeDesc(USER_ID);
        verify(repository, never()).findAllByOrderByStartTimeDesc();
    }

    @Test
    void list_privilegedUser_returnsAllBookings() {
        // Arrange
        when(currentUser.isPrivileged()).thenReturn(true);
        when(repository.findAllByOrderByStartTimeDesc()).thenReturn(List.of(
                booking(1L, USER_ID, BookingStatus.PENDING),
                booking(2L, OTHER_ID, BookingStatus.CONFIRMED)));

        // Act
        List<BookingDto> result = service.list();

        // Assert
        assertThat(result).extracting(BookingDto::ownerId).containsExactly(USER_ID, OTHER_ID);
        verify(repository, never()).findByOwnerIdOrderByStartTimeDesc(any());
    }

    @Test
    void list_userWithoutBookings_returnsEmptyList() {
        // Arrange
        when(currentUser.isPrivileged()).thenReturn(false);
        when(currentUser.id()).thenReturn(USER_ID);
        when(repository.findByOwnerIdOrderByStartTimeDesc(USER_ID)).thenReturn(List.of());

        // Act
        List<BookingDto> result = service.list();

        // Assert
        assertThat(result).isEmpty();
    }

    // ================= get =================
    @Test
    void get_ownBooking_returnsDtoWithRoomData() {
        // Arrange
        when(currentUser.isPrivileged()).thenReturn(false);
        when(currentUser.id()).thenReturn(USER_ID);
        when(repository.findById(1L)).thenReturn(Optional.of(booking(1L, USER_ID, BookingStatus.PENDING)));

        // Act
        BookingDto result = service.get(1L);

        // Assert
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.roomId()).isEqualTo(1L);
        assertThat(result.roomName()).isEqualTo("Alpha");
        assertThat(result.status()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void get_foreignBookingAsPrivilegedUser_returnsDto() {
        // Arrange
        when(currentUser.isPrivileged()).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(booking(1L, OTHER_ID, BookingStatus.PENDING)));

        // Act
        BookingDto result = service.get(1L);

        // Assert
        assertThat(result.ownerId()).isEqualTo(OTHER_ID);
    }

    @Test
    void get_bookingNotFound_throwsNotFoundException() {
        // Arrange
        when(repository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Booking 99 not found");
    }

    @Test
    void get_foreignBookingAsRegularUser_throwsAccessDenied() {
        // Arrange
        when(currentUser.isPrivileged()).thenReturn(false);
        when(currentUser.id()).thenReturn(USER_ID);
        when(repository.findById(1L)).thenReturn(Optional.of(booking(1L, OTHER_ID, BookingStatus.PENDING)));

        // Act + Assert
        assertThatThrownBy(() -> service.get(1L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Not your booking");
    }

    // ================= create =================
    @Test
    void create_validData_savesPendingBookingForCurrentUser() {
        // Arrange
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room(1L, true)));
        when(currentUser.id()).thenReturn(USER_ID);
        when(repository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BookingDto result = service.create(request(1L, START, END));

        // Assert: що саме передано в repository.save
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(repository).save(captor.capture());
        Booking saved = captor.getValue();
        assertThat(saved.getOwnerId()).isEqualTo(USER_ID);
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(saved.getRoom().getId()).isEqualTo(1L);
        assertThat(saved.getStartTime()).isEqualTo(START);
        assertThat(saved.getEndTime()).isEqualTo(END);
        // і що повернуто клієнту
        assertThat(result.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(result.roomName()).isEqualTo("Alpha");
    }

    @Test
    void create_clientSuppliesOwnerAndStatus_valuesAreIgnored() {                                                                                                                           //mass assignment
        // Arrange: клієнт намагається підмінити власника і статус
        BookingDto malicious = new BookingDto(null, 1L, null, "hacker", START, END, BookingStatus.CONFIRMED, null);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room(1L, true)));
        when(currentUser.id()).thenReturn(USER_ID);
        when(repository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.create(malicious);

        // Assert
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getOwnerId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void create_endBeforeStart_throwsIllegalArgumentAndSavesNothing() {
        // Arrange
        BookingDto invalid = request(1L, END, START);

        // Act + Assert
        assertThatThrownBy(() -> service.create(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("endTime must be after startTime");
        verifyNoInteractions(repository, roomRepository);
    }

    @Test
    void create_endEqualsStart_throwsIllegalArgument() {
        // Arrange: межове значення - нульова тривалість
        BookingDto zeroLength = request(1L, START, START);

        // Act + Assert
        assertThatThrownBy(() -> service.create(zeroLength))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("endTime must be after startTime");
    }

    @Test
    void create_roomNotFound_throwsNotFoundAndSavesNothing() {
        // Arrange
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.create(request(99L, START, END)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Room 99 not found");
        verify(repository, never()).save(any(Booking.class));
    }

    @Test
    void create_inactiveRoom_throwsIllegalArgumentAndSavesNothing() {
        // Arrange
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room(1L, false)));

        // Act + Assert
        assertThatThrownBy(() -> service.create(request(1L, START, END)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Room is not active");
        verify(repository, never()).save(any(Booking.class));
    }

    // ================= update =================
    @Test
    void update_pendingOwnBooking_updatesTimesAndSaves() {
        // Arrange
        Booking existing = booking(1L, USER_ID, BookingStatus.PENDING);
        LocalDateTime newStart = START.plusDays(1);
        LocalDateTime newEnd = newStart.plusHours(2);
        when(currentUser.isPrivileged()).thenReturn(false);
        when(currentUser.id()).thenReturn(USER_ID);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room(1L, true)));
        when(repository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BookingDto result = service.update(1L, request(1L, newStart, newEnd));

        // Assert
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(repository).save(captor.capture());
        Booking saved = captor.getValue();
        assertThat(saved.getStartTime()).isEqualTo(newStart);
        assertThat(saved.getEndTime()).isEqualTo(newEnd);
        assertThat(saved.getOwnerId()).isEqualTo(USER_ID);
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(result.startTime()).isEqualTo(newStart);
    }
    @Test
    void update_differentRoom_assignsNewRoomToBooking() {
        // Arrange
        Booking existing = booking(1L, USER_ID, BookingStatus.PENDING); // кімната 1
        Room newRoom = room(2L, true);
        when(currentUser.isPrivileged()).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(roomRepository.findById(2L)).thenReturn(Optional.of(newRoom));
        when(repository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BookingDto result = service.update(1L, request(2L, START, END));

        // Assert
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRoom().getId()).isEqualTo(2L);
        assertThat(result.roomId()).isEqualTo(2L);
    }

    @Test
    void update_confirmedBooking_throwsIllegalStateAndSavesNothing() {
        // Arrange
        when(currentUser.isPrivileged()).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(booking(1L, USER_ID, BookingStatus.CONFIRMED)));

        // Act + Assert
        assertThatThrownBy(() -> service.update(1L, request(1L, START, END)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only PENDING bookings can be edited");
        verify(repository, never()).save(any(Booking.class));
    }

    @Test
    void update_endBeforeStart_throwsIllegalArgument() {
        // Arrange
        when(currentUser.isPrivileged()).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(booking(1L, USER_ID, BookingStatus.PENDING)));

        // Act + Assert
        assertThatThrownBy(() -> service.update(1L, request(1L, END, START)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("endTime must be after startTime");
        verify(repository, never()).save(any(Booking.class));
    }

    @Test
    void update_roomNotFound_throwsNotFound() {
        // Arrange
        when(currentUser.isPrivileged()).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(booking(1L, USER_ID, BookingStatus.PENDING)));
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.update(1L, request(99L, START, END)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Room 99 not found");
        verify(repository, never()).save(any(Booking.class));
    }
    @Test
    void update_inactiveRoom_throwsIllegalArgumentAndSavesNothing() {
        // Arrange
        when(currentUser.isPrivileged()).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(booking(1L, USER_ID, BookingStatus.PENDING)));
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room(2L, false)));

        // Act + Assert
        assertThatThrownBy(() -> service.update(1L, request(2L, START, END)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Room is not active");
        verify(repository, never()).save(any(Booking.class));
    }
    @Test
    void update_foreignBookingAsRegularUser_throwsAccessDenied() {
        // Arrange
        when(currentUser.isPrivileged()).thenReturn(false);
        when(currentUser.id()).thenReturn(USER_ID);
        when(repository.findById(1L)).thenReturn(Optional.of(booking(1L, OTHER_ID, BookingStatus.PENDING)));

        // Act + Assert
        assertThatThrownBy(() -> service.update(1L, request(1L, START, END)))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).save(any(Booking.class));
    }

    @Test
    void update_bookingNotFound_throwsNotFound() {
        // Arrange
        when(repository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.update(99L, request(1L, START, END)))
                .isInstanceOf(NotFoundException.class);
    }

    // ================= delete =================
    @Test
    void delete_ownBooking_deletesIt() {
        // Arrange
        Booking existing = booking(1L, USER_ID, BookingStatus.PENDING);
        when(currentUser.isPrivileged()).thenReturn(false);
        when(currentUser.id()).thenReturn(USER_ID);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        // Act
        service.delete(1L);

        // Assert: видалено саме цю сутність
        verify(repository).delete(existing);
    }

    @Test
    void delete_foreignBookingAsRegularUser_throwsAccessDeniedAndDeletesNothing() {
        // Arrange
        when(currentUser.isPrivileged()).thenReturn(false);
        when(currentUser.id()).thenReturn(USER_ID);
        when(repository.findById(1L)).thenReturn(Optional.of(booking(1L, OTHER_ID, BookingStatus.PENDING)));

        // Act + Assert
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).delete(any(Booking.class));
    }

    @Test
    void delete_bookingNotFound_throwsNotFoundAndDeletesNothing() {
        // Arrange
        when(repository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(NotFoundException.class);
        verify(repository, never()).delete(any(Booking.class));
    }

    // ================= confirm =================
    @Test
    void confirm_pendingBooking_setsStatusConfirmed() {
        // Arrange
        Booking existing = booking(1L, USER_ID, BookingStatus.PENDING);
        when(currentUser.isPrivileged()).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        // Act
        BookingDto result = service.confirm(1L);

        // Assert
        assertThat(existing.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result.status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CONFIRMED", "CANCELLED"})
    void confirm_nonPendingBooking_throwsIllegalStateAndKeepsStatus(BookingStatus status) {
        // Arrange
        Booking existing = booking(1L, USER_ID, status);
        when(currentUser.isPrivileged()).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only PENDING bookings can be confirmed");
        assertThat(existing.getStatus()).isEqualTo(status);
    }

    @Test
    void confirm_bookingNotFound_throwsNotFound() {
        // Arrange
        when(repository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.confirm(99L)).isInstanceOf(NotFoundException.class);
    }

    // ================= cancel =================
    @Test
    void cancel_ownBooking_setsStatusCancelled() {
        // Arrange
        Booking existing = booking(1L, USER_ID, BookingStatus.PENDING);
        when(currentUser.isPrivileged()).thenReturn(false);
        when(currentUser.id()).thenReturn(USER_ID);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        // Act
        BookingDto result = service.cancel(1L);

        // Assert
        assertThat(existing.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancel_foreignBookingAsPrivilegedUser_setsStatusCancelled() {
        // Arrange
        Booking existing = booking(1L, OTHER_ID, BookingStatus.PENDING);
        when(currentUser.isPrivileged()).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        // Act
        service.cancel(1L);

        // Assert
        assertThat(existing.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancel_foreignBookingAsRegularUser_throwsAccessDeniedAndKeepsStatus() {
        // Arrange
        Booking existing = booking(1L, OTHER_ID, BookingStatus.PENDING);
        when(currentUser.isPrivileged()).thenReturn(false);
        when(currentUser.id()).thenReturn(USER_ID);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        // Act + Assert
        assertThatThrownBy(() -> service.cancel(1L)).isInstanceOf(AccessDeniedException.class);
        assertThat(existing.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void cancel_bookingNotFound_throwsNotFound() {
        // Arrange
        when(repository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.cancel(99L)).isInstanceOf(NotFoundException.class);
    }
}
