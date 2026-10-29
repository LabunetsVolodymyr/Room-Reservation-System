package com.example.booking.booking;

import com.example.booking.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit-тести BookingController: сервіс замокано, Spring-контекст не запускається. */
@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 12, 1, 10, 0);
    private static final LocalDateTime END = START.plusHours(1);

    @Mock
    private BookingService service;
    @InjectMocks
    private BookingController controller;

    private BookingDto dto(Long id, BookingStatus status) {
        return new BookingDto(id, 1L, "Alpha", "user-1", START, END, status, START);
    }

    @Test
    void list_delegatesToService() {
        // Arrange
        List<BookingDto> bookings = List.of(dto(1L, BookingStatus.PENDING));
        when(service.list()).thenReturn(bookings);

        // Act + Assert
        assertThat(controller.list()).isEqualTo(bookings);
    }

    @Test
    void get_existingId_returnsBooking() {
        // Arrange
        when(service.get(1L)).thenReturn(dto(1L, BookingStatus.PENDING));

        // Act + Assert
        assertThat(controller.get(1L).id()).isEqualTo(1L);
    }

    @Test
    void get_serviceThrowsNotFound_exceptionPropagates() {
        // Arrange
        when(service.get(99L)).thenThrow(new NotFoundException("Booking 99 not found"));

        // Act + Assert
        assertThatThrownBy(() -> controller.get(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_validDto_returns201WithLocationHeader() {
        // Arrange
        BookingDto request = new BookingDto(null, 1L, null, null, START, END, null, null);
        BookingDto created = dto(10L, BookingStatus.PENDING);
        when(service.create(request)).thenReturn(created);

        // Act
        ResponseEntity<BookingDto> response = controller.create(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/api/bookings/10"));
        assertThat(response.getBody()).isEqualTo(created);
    }

    @Test
    void update_validDto_returnsUpdatedBooking() {
        // Arrange
        BookingDto request = new BookingDto(null, 1L, null, null, START, END, null, null);
        BookingDto updated = dto(1L, BookingStatus.PENDING);
        when(service.update(1L, request)).thenReturn(updated);

        // Act + Assert
        assertThat(controller.update(1L, request)).isEqualTo(updated);
    }

    @Test
    void delete_existingId_returns204AndCallsService() {
        // Act
        ResponseEntity<Void> response = controller.delete(4L);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(4L);
    }

    @Test
    void confirm_existingId_returnsConfirmedBooking() {
        // Arrange
        when(service.confirm(1L)).thenReturn(dto(1L, BookingStatus.CONFIRMED));

        // Act + Assert
        assertThat(controller.confirm(1L).status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void cancel_existingId_returnsCancelledBooking() {
        // Arrange
        when(service.cancel(1L)).thenReturn(dto(1L, BookingStatus.CANCELLED));

        // Act + Assert
        assertThat(controller.cancel(1L).status()).isEqualTo(BookingStatus.CANCELLED);
    }
}
