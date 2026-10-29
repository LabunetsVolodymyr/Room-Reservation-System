package com.example.booking.room;

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

/** Unit-тести RoomController: сервіс замокано, HTTP-шар (MockMvc) і Spring-контекст не потрібні. */
@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 1, 1, 9, 0);

    @Mock
    private RoomService service;
    @InjectMocks
    private RoomController controller;

    @Test
    void list_delegatesToServiceAndReturnsResult() {
        // Arrange
        List<RoomDto> rooms = List.of(new RoomDto(1L, "Alpha", 6, null, true, CREATED));
        when(service.list()).thenReturn(rooms);

        // Act
        List<RoomDto> result = controller.list();

        // Assert
        assertThat(result).isEqualTo(rooms);
    }

    @Test
    void get_existingId_returnsRoomFromService() {
        // Arrange
        RoomDto room = new RoomDto(1L, "Alpha", 6, null, true, CREATED);
        when(service.get(1L)).thenReturn(room);

        // Act + Assert
        assertThat(controller.get(1L)).isEqualTo(room);
    }

    @Test
    void get_serviceThrowsNotFound_exceptionPropagates() {
        // Arrange: контролер не обробляє помилку, це робить GlobalExceptionHandler
        when(service.get(99L)).thenThrow(new NotFoundException("Room 99 not found"));

        // Act + Assert
        assertThatThrownBy(() -> controller.get(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_validDto_returns201WithLocationHeader() {
        // Arrange
        RoomDto request = new RoomDto(null, "Gamma", 10, null, true, null);
        RoomDto created = new RoomDto(5L, "Gamma", 10, null, true, CREATED);
        when(service.create(request)).thenReturn(created);

        // Act
        ResponseEntity<RoomDto> response = controller.create(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/api/rooms/5"));
        assertThat(response.getBody()).isEqualTo(created);
    }

    @Test
    void update_validDto_returnsUpdatedRoom() {
        // Arrange
        RoomDto request = new RoomDto(null, "Alpha-2", 8, null, true, null);
        RoomDto updated = new RoomDto(1L, "Alpha-2", 8, null, true, CREATED);
        when(service.update(1L, request)).thenReturn(updated);

        // Act + Assert
        assertThat(controller.update(1L, request)).isEqualTo(updated);
    }

    @Test
    void delete_existingId_returns204AndCallsService() {
        // Act
        ResponseEntity<Void> response = controller.delete(3L);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(service).delete(3L);
    }
}
