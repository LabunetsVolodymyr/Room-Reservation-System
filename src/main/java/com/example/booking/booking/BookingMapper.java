package com.example.booking.booking;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/** MapStruct-мапер бронювання. Реалізацію BookingMapperImpl генерує компілятор (див. RoomMapper). */
@Mapper(componentModel = "spring")
public interface BookingMapper {

    // Entity -> DTO. Поля з різними іменами вказуємо явно через "крапкову" навігацію:
    //   booking.room.id -> dto.roomId;  booking.room.name -> dto.roomName
    @Mapping(source = "room.id", target = "roomId")
    @Mapping(source = "room.name", target = "roomName")
    BookingDto toDto(Booking booking);

    List<BookingDto> toDtoList(List<Booking> bookings);

    // DTO -> нова Entity. Ці поля сервіс виставляє сам (безпека: клієнт не може підмінити власника чи статус):
    //   id - генерує БД; room - сервіс завантажує за roomId; ownerId - з токена; status - PENDING; createdAt - @PrePersist
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "ownerId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Booking toEntity(BookingDto dto);

    // Оновлення існуючого бронювання: змінюються лише startTime і endTime (решта ігнорується з тих самих причин)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "ownerId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void update(BookingDto dto, @MappingTarget Booking booking);
}
