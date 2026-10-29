package com.example.booking.room;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-10-29T19:19:11+0200",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.12.1 (Eclipse Adoptium)"
)
@Component
public class RoomMapperImpl implements RoomMapper {

    @Override
    public RoomDto toDto(Room room) {
        if ( room == null ) {
            return null;
        }

        Long id = null;
        String name = null;
        int capacity = 0;
        String description = null;
        Boolean active = null;
        LocalDateTime createdAt = null;

        id = room.getId();
        name = room.getName();
        capacity = room.getCapacity();
        description = room.getDescription();
        active = room.isActive();
        createdAt = room.getCreatedAt();

        RoomDto roomDto = new RoomDto( id, name, capacity, description, active, createdAt );

        return roomDto;
    }

    @Override
    public List<RoomDto> toDtoList(List<Room> rooms) {
        if ( rooms == null ) {
            return null;
        }

        List<RoomDto> list = new ArrayList<RoomDto>( rooms.size() );
        for ( Room room : rooms ) {
            list.add( toDto( room ) );
        }

        return list;
    }

    @Override
    public Room toEntity(RoomDto dto) {
        if ( dto == null ) {
            return null;
        }

        Room room = new Room();

        room.setName( dto.name() );
        room.setCapacity( dto.capacity() );
        room.setDescription( dto.description() );
        if ( dto.active() != null ) {
            room.setActive( dto.active() );
        }

        return room;
    }

    @Override
    public void update(RoomDto dto, Room room) {
        if ( dto == null ) {
            return;
        }

        room.setName( dto.name() );
        room.setCapacity( dto.capacity() );
        room.setDescription( dto.description() );
        if ( dto.active() != null ) {
            room.setActive( dto.active() );
        }
    }
}
