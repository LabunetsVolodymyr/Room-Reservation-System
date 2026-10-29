package com.example.booking.booking;

import com.example.booking.room.Room;
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
public class BookingMapperImpl implements BookingMapper {

    @Override
    public BookingDto toDto(Booking booking) {
        if ( booking == null ) {
            return null;
        }

        Long roomId = null;
        String roomName = null;
        Long id = null;
        String ownerId = null;
        LocalDateTime startTime = null;
        LocalDateTime endTime = null;
        BookingStatus status = null;
        LocalDateTime createdAt = null;

        roomId = bookingRoomId( booking );
        roomName = bookingRoomName( booking );
        id = booking.getId();
        ownerId = booking.getOwnerId();
        startTime = booking.getStartTime();
        endTime = booking.getEndTime();
        status = booking.getStatus();
        createdAt = booking.getCreatedAt();

        BookingDto bookingDto = new BookingDto( id, roomId, roomName, ownerId, startTime, endTime, status, createdAt );

        return bookingDto;
    }

    @Override
    public List<BookingDto> toDtoList(List<Booking> bookings) {
        if ( bookings == null ) {
            return null;
        }

        List<BookingDto> list = new ArrayList<BookingDto>( bookings.size() );
        for ( Booking booking : bookings ) {
            list.add( toDto( booking ) );
        }

        return list;
    }

    @Override
    public Booking toEntity(BookingDto dto) {
        if ( dto == null ) {
            return null;
        }

        Booking booking = new Booking();

        booking.setStartTime( dto.startTime() );
        booking.setEndTime( dto.endTime() );

        return booking;
    }

    @Override
    public void update(BookingDto dto, Booking booking) {
        if ( dto == null ) {
            return;
        }

        booking.setStartTime( dto.startTime() );
        booking.setEndTime( dto.endTime() );
    }

    private Long bookingRoomId(Booking booking) {
        Room room = booking.getRoom();
        if ( room == null ) {
            return null;
        }
        return room.getId();
    }

    private String bookingRoomName(Booking booking) {
        Room room = booking.getRoom();
        if ( room == null ) {
            return null;
        }
        return room.getName();
    }
}
