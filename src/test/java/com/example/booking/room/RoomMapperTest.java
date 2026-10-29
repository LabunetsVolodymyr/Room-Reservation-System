package com.example.booking.room;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit-тести згенерованого MapStruct-мапера (без Spring). Час фіксований. */
class RoomMapperTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 1, 1, 9, 0);

    private final RoomMapper mapper = Mappers.getMapper(RoomMapper.class);

    private Room room() {
        Room room = new Room();
        room.setId(1L);
        room.setName("Alpha");
        room.setCapacity(6);
        room.setDescription("Small");
        room.setActive(false);
        return room;
    }

    @Test
    void toDto_validRoom_mapsAllFields() {
        // Act
        RoomDto dto = mapper.toDto(room());

        // Assert
        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.name()).isEqualTo("Alpha");
        assertThat(dto.capacity()).isEqualTo(6);
        assertThat(dto.description()).isEqualTo("Small");
        assertThat(dto.active()).isFalse();
    }

    @Test
    void toDto_nullRoom_returnsNull() {
        assertThat(mapper.toDto(null)).isNull();
    }

    @Test
    void toDtoList_twoRooms_mapsEachElement() {
        // Act
        List<RoomDto> result = mapper.toDtoList(List.of(room(), room()));

        // Assert
        assertThat(result).hasSize(2);
    }

    @Test
    void toEntity_dtoWithIdAndCreatedAt_ignoresBothFields() {
        // Arrange
        RoomDto dto = new RoomDto(7L, "Gamma", 10, "Big", true, CREATED);

        // Act
        Room entity = mapper.toEntity(dto);

        // Assert
        assertThat(entity.getId()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getName()).isEqualTo("Gamma");
        assertThat(entity.getCapacity()).isEqualTo(10);
        assertThat(entity.isActive()).isTrue();
    }

    @Test
    void update_dtoWithNullActive_keepsExistingActiveValue() {
        // Arrange
        Room existing = room(); // active = false
        RoomDto dto = new RoomDto(99L, "Alpha-2", 8, "new", null, null);

        // Act
        mapper.update(dto, existing);

        // Assert
        assertThat(existing.getId()).isEqualTo(1L);
        assertThat(existing.getName()).isEqualTo("Alpha-2");
        assertThat(existing.isActive()).isFalse();
    }
}
