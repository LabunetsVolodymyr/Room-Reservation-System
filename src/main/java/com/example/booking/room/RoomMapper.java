package com.example.booking.room;

import org.mapstruct.Mapper;        // позначає інтерфейс як мапер
import org.mapstruct.Mapping;       // налаштування відповідності окремих полів
import org.mapstruct.MappingTarget; // "цільовий" об'єкт, який оновлюється на місці

import java.util.List;

/**
 * MapStruct - генератор коду мапінгу Entity <-> DTO. Під час КОМПІЛЯЦІЇ (annotation processor) створює клас
 * RoomMapperImpl зі звичайним Java-кодом (get/set) без рефлексії, тому працює швидко і помилки видно одразу.
 * componentModel = "spring" - згенерований клас стає Spring-бін, його можна інжектити через конструктор.
 */
@Mapper(componentModel = "spring")
public interface RoomMapper {

    // Entity -> DTO: поля з однаковими іменами копіюються автоматично
    RoomDto toDto(Room room);

    // Список: MapStruct сам викликає toDto для кожного елемента
    List<RoomDto> toDtoList(List<Room> rooms);

    // DTO -> нова Entity. id і createdAt керуються БД/колбеком, тому з DTO їх не беремо (ignore)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Room toEntity(RoomDto dto);

    // Оновлення ІСНУЮЧОЇ сутності даними з DTO (@MappingTarget). Повертає void: змінює переданий об'єкт.
    // Для null у Boolean active MapStruct не викликає setActive (primitive), тож значення зберігається.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void update(RoomDto dto, @MappingTarget Room room);
}
