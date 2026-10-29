package com.example.booking.room;

import com.example.booking.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тести RoomService: репозиторій замокано, мапер справжній (MapStruct), Spring-контекст не потрібен.
 * Анотації @PreAuthorize тут не перевіряються (діють лише в Spring-проксі).
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository repository;
    @Spy
    private RoomMapper mapper = Mappers.getMapper(RoomMapper.class);

    @InjectMocks
    private RoomService service;

    private Room room(Long id, String name, int capacity, String description, boolean active) {
        Room room = new Room();
        room.setId(id);
        room.setName(name);
        room.setCapacity(capacity);
        room.setDescription(description);
        room.setActive(active);
        return room;
    }

    // ================= list =================
    @Test
    void list_existingRooms_returnsMappedDtos() {
        // Arrange
        when(repository.findAll()).thenReturn(List.of(
                room(1L, "Alpha", 6, "Small", true),
                room(2L, "Beta", 12, "Large", true)));

        // Act
        List<RoomDto> result = service.list();

        // Assert
        assertThat(result).extracting(RoomDto::name).containsExactly("Alpha", "Beta");
        assertThat(result).extracting(RoomDto::capacity).containsExactly(6, 12);
    }

    @Test
    void list_noRooms_returnsEmptyList() {
        // Arrange
        when(repository.findAll()).thenReturn(List.of());

        // Act
        List<RoomDto> result = service.list();

        // Assert
        assertThat(result).isEmpty();
    }

    // ================= get =================
    @Test
    void get_existingRoom_returnsDto() {
        // Arrange
        when(repository.findById(1L)).thenReturn(Optional.of(room(1L, "Alpha", 6, "Small", true)));

        // Act
        RoomDto result = service.get(1L);

        // Assert
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Alpha");
        assertThat(result.active()).isTrue();
    }

    @Test
    void get_roomNotFound_throwsNotFoundException() {
        // Arrange
        when(repository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Room 99 not found");
    }

    // ================= create =================
    @Test
    void create_validDto_savesRoomAndReturnsDtoWithGeneratedId() {
        // Arrange
        RoomDto request = new RoomDto(null, "Gamma", 10, "Big room", true, null);
        when(repository.saveAndFlush(any(Room.class))).thenAnswer(invocation -> {
            Room saved = invocation.getArgument(0);
            saved.setId(5L); // імітуємо генерацію id базою даних
            return saved;
        });

        // Act
        RoomDto result = service.create(request);

        // Assert
        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Gamma");
        assertThat(captor.getValue().getCapacity()).isEqualTo(10);
        assertThat(captor.getValue().getDescription()).isEqualTo("Big room");
        assertThat(result.id()).isEqualTo(5L);
    }

    @Test
    void create_activeNotProvided_roomIsActiveByDefault() {
        // Arrange: у запиті немає поля active (null)
        RoomDto request = new RoomDto(null, "Gamma", 10, null, null, null);
        when(repository.saveAndFlush(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.create(request);

        // Assert
        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void create_duplicateName_propagatesDataIntegrityViolation() {
        // Arrange: БД відхиляє дубль назви
        when(repository.saveAndFlush(any(Room.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        // Act + Assert
        assertThatThrownBy(() -> service.create(new RoomDto(null, "Alpha", 6, null, true, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ================= update =================
    @Test
    void update_existingRoom_updatesFieldsAndKeepsId() {
        // Arrange
        Room existing = room(1L, "Alpha", 6, "old", true);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RoomDto request = new RoomDto(99L, "Alpha-2", 8, "new", null, null); // id із запиту має ігноруватися

        // Act
        RoomDto result = service.update(1L, request);

        // Assert
        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(existing.getId()).isEqualTo(1L);
        assertThat(existing.getName()).isEqualTo("Alpha-2");
        assertThat(existing.getCapacity()).isEqualTo(8);
        assertThat(existing.getDescription()).isEqualTo("new");
        assertThat(existing.isActive()).isTrue(); // active не передано -> не змінюється
        assertThat(result.name()).isEqualTo("Alpha-2");
    }

    @Test
    void update_roomNotFound_throwsNotFoundAndSavesNothing() {
        // Arrange
        when(repository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.update(99L, new RoomDto(null, "X", 1, null, true, null)))
                .isInstanceOf(NotFoundException.class);
        verify(repository, never()).saveAndFlush(any(Room.class));
    }

    // ================= delete =================
    @Test
    void delete_existingRoom_deletesAndFlushesInOrder() {
        // Arrange
        Room existing = room(1L, "Alpha", 6, "Small", true);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        // Act
        service.delete(1L);

        // Assert
        InOrder order = inOrder(repository);
        order.verify(repository).delete(existing);
        order.verify(repository).flush();
    }

    @Test
    void delete_roomNotFound_throwsNotFoundAndDeletesNothing() {
        // Arrange
        when(repository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(NotFoundException.class);
        verify(repository, never()).delete(any(Room.class));
    }

    @Test
    void delete_roomHasBookings_propagatesDataIntegrityViolation() {
        // Arrange: при flush БД відхиляє видалення через зовнішній ключ
        when(repository.findById(1L)).thenReturn(Optional.of(room(1L, "Alpha", 6, "Small", true)));
        doThrow(new DataIntegrityViolationException("fk")).when(repository).flush();

        // Act + Assert
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
