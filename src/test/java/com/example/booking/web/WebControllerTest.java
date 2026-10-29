package com.example.booking.web;

import com.example.booking.booking.BookingDto;
import com.example.booking.booking.BookingService;
import com.example.booking.booking.BookingStatus;
import com.example.booking.common.NotFoundException;
import com.example.booking.room.RoomDto;
import com.example.booking.room.RoomService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-тести Thymeleaf-контролера: сервіси замокано, Model/RedirectAttributes - прості реалізації Spring
 * (без контексту, сервера й шаблонізатора). Перевіряємо, яка сторінка повертається і що потрапляє у flash-повідомлення.
 */
@ExtendWith(MockitoExtension.class)
class WebControllerTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 12, 1, 10, 0);
    private static final LocalDateTime END = START.plusHours(1);

    @Mock
    private RoomService roomService;
    @Mock
    private BookingService bookingService;
    @InjectMocks
    private WebController controller;

    private final RoomDto roomDto = new RoomDto(null, "Gamma", 10, null, true, null);
    private final BookingDto bookingDto = new BookingDto(null, 1L, null, null, START, END, null, null);

    private BindingResult noErrors(Object target) {
        return new BeanPropertyBindingResult(target, "dto");
    }

    private BindingResult withErrors(Object target) {
        BindingResult errors = new BeanPropertyBindingResult(target, "dto");
        errors.reject("invalid");
        return errors;
    }

    // ================= прості сторінки =================
    @Test
    void home_always_redirectsToRooms() {
        assertThat(controller.home()).isEqualTo("redirect:/rooms");
    }

    @Test
    void admin_always_returnsAdminView() {
        assertThat(controller.admin()).isEqualTo("admin");
    }

    @Test
    void rooms_existingRooms_putsThemIntoModel() {
        // Arrange
        Model model = new ExtendedModelMap();
        List<RoomDto> rooms = List.of(new RoomDto(1L, "Alpha", 6, null, true, null));
        when(roomService.list()).thenReturn(rooms);

        // Act
        String view = controller.rooms(model);

        // Assert
        assertThat(view).isEqualTo("rooms");
        assertThat(model.getAttribute("rooms")).isEqualTo(rooms);
    }

    @Test
    void bookings_existingData_putsBookingsAndRoomsIntoModel() {
        // Arrange
        Model model = new ExtendedModelMap();
        List<BookingDto> bookings = List.of(new BookingDto(1L, 1L, "Alpha", "u", START, END, BookingStatus.PENDING, START));
        List<RoomDto> rooms = List.of(new RoomDto(1L, "Alpha", 6, null, true, null));
        when(bookingService.list()).thenReturn(bookings);
        when(roomService.list()).thenReturn(rooms);

        // Act
        String view = controller.bookings(model);

        // Assert
        assertThat(view).isEqualTo("bookings");
        assertThat(model.getAttribute("bookings")).isEqualTo(bookings);
        assertThat(model.getAttribute("rooms")).isEqualTo(rooms);
    }

    // ================= rooms: create / update / delete =================
    @Test
    void createRoom_validData_callsServiceAndFlashesMessage() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // Act
        String view = controller.createRoom(roomDto, noErrors(roomDto), ra);

        // Assert
        assertThat(view).isEqualTo("redirect:/rooms");
        verify(roomService).create(roomDto);
        assertThat(ra.getFlashAttributes()).containsEntry("msg", "Room created");
    }

    @Test
    void createRoom_validationErrors_flashesErrorAndSkipsService() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // Act
        String view = controller.createRoom(roomDto, withErrors(roomDto), ra);

        // Assert
        assertThat(view).isEqualTo("redirect:/rooms");
        verifyNoInteractions(roomService);
        assertThat(ra.getFlashAttributes()).containsEntry("error", "Invalid room data");
    }

    @Test
    void createRoom_duplicateName_flashesGenericError() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        when(roomService.create(roomDto)).thenThrow(new DataIntegrityViolationException("duplicate"));

        // Act
        String view = controller.createRoom(roomDto, noErrors(roomDto), ra);

        // Assert
        assertThat(view).isEqualTo("redirect:/rooms");
        assertThat(ra.getFlashAttributes()).containsEntry("error", "Duplicate or invalid data");
    }

    @Test
    void updateRoom_validData_callsServiceAndFlashesMessage() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // Act
        String view = controller.updateRoom(1L, roomDto, noErrors(roomDto), ra);

        // Assert
        assertThat(view).isEqualTo("redirect:/rooms");
        verify(roomService).update(1L, roomDto);
        assertThat(ra.getFlashAttributes()).containsEntry("msg", "Room updated");
    }

    @Test
    void updateRoom_validationErrors_flashesErrorAndSkipsService() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // Act
        controller.updateRoom(1L, roomDto, withErrors(roomDto), ra);

        // Assert
        verifyNoInteractions(roomService);
        assertThat(ra.getFlashAttributes()).containsEntry("error", "Invalid room data");
    }

    @Test
    void deleteRoom_existingRoom_callsServiceAndFlashesMessage() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // Act
        String view = controller.deleteRoom(1L, ra);

        // Assert
        assertThat(view).isEqualTo("redirect:/rooms");
        verify(roomService).delete(1L);
        assertThat(ra.getFlashAttributes()).containsEntry("msg", "Room deleted");
    }

    @Test
    void deleteRoom_roomNotFound_flashesNotFoundMessage() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        doThrow(new NotFoundException("Room 9 not found")).when(roomService).delete(9L);

        // Act
        controller.deleteRoom(9L, ra);

        // Assert
        assertThat(ra.getFlashAttributes()).containsEntry("error", "Room 9 not found");
    }

    // ================= bookings: create / update / confirm / cancel / delete =================
    @Test
    void createBooking_validData_callsServiceAndFlashesMessage() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // Act
        String view = controller.createBooking(bookingDto, noErrors(bookingDto), ra);

        // Assert
        assertThat(view).isEqualTo("redirect:/bookings");
        verify(bookingService).create(bookingDto);
        assertThat(ra.getFlashAttributes()).containsEntry("msg", "Booking created");
    }

    @Test
    void createBooking_validationErrors_flashesErrorAndSkipsService() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // Act
        controller.createBooking(bookingDto, withErrors(bookingDto), ra);

        // Assert
        verifyNoInteractions(bookingService);
        assertThat(ra.getFlashAttributes()).containsEntry("error", "Invalid booking data");
    }

    @Test
    void createBooking_serviceRejectsTimes_flashesServiceMessage() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        when(bookingService.create(bookingDto)).thenThrow(new IllegalArgumentException("endTime must be after startTime"));

        // Act
        controller.createBooking(bookingDto, noErrors(bookingDto), ra);

        // Assert
        assertThat(ra.getFlashAttributes()).containsEntry("error", "endTime must be after startTime");
    }

    @Test
    void updateBooking_validData_callsServiceAndFlashesMessage() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // Act
        String view = controller.updateBooking(1L, bookingDto, noErrors(bookingDto), ra);

        // Assert
        assertThat(view).isEqualTo("redirect:/bookings");
        verify(bookingService).update(1L, bookingDto);
        assertThat(ra.getFlashAttributes()).containsEntry("msg", "Booking updated");
    }

    @Test
    void updateBooking_validationErrors_flashesErrorAndSkipsService() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // Act
        controller.updateBooking(1L, bookingDto, withErrors(bookingDto), ra);

        // Assert
        verifyNoInteractions(bookingService);
        assertThat(ra.getFlashAttributes()).containsEntry("error", "Invalid booking data");
    }

    @Test
    void confirmBooking_pendingBooking_callsServiceAndFlashesMessage() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // Act
        String view = controller.confirmBooking(1L, ra);

        // Assert
        assertThat(view).isEqualTo("redirect:/bookings");
        verify(bookingService).confirm(1L);
        assertThat(ra.getFlashAttributes()).containsEntry("msg", "Booking confirmed");
    }

    @Test
    void confirmBooking_alreadyConfirmed_flashesStateError() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        when(bookingService.confirm(1L)).thenThrow(new IllegalStateException("Only PENDING bookings can be confirmed"));

        // Act
        controller.confirmBooking(1L, ra);

        // Assert
        assertThat(ra.getFlashAttributes()).containsEntry("error", "Only PENDING bookings can be confirmed");
    }

    @Test
    void confirmBooking_accessDenied_exceptionPropagatesToSpringSecurity() {
        // Arrange: AccessDeniedException свідомо не ловиться, щоб Spring Security показав 403
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        when(bookingService.confirm(1L)).thenThrow(new AccessDeniedException("denied"));

        // Act + Assert
        assertThatThrownBy(() -> controller.confirmBooking(1L, ra)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancelBooking_existingBooking_callsServiceAndFlashesMessage() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // Act
        String view = controller.cancelBooking(1L, ra);

        // Assert
        assertThat(view).isEqualTo("redirect:/bookings");
        verify(bookingService).cancel(1L);
        assertThat(ra.getFlashAttributes()).containsEntry("msg", "Booking cancelled");
    }

    @Test
    void deleteBooking_existingBooking_callsServiceAndFlashesMessage() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // Act
        String view = controller.deleteBooking(1L, ra);

        // Assert
        assertThat(view).isEqualTo("redirect:/bookings");
        verify(bookingService).delete(1L);
        assertThat(ra.getFlashAttributes()).containsEntry("msg", "Booking deleted");
    }

    @Test
    void deleteBooking_bookingNotFound_flashesNotFoundMessage() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        doThrow(new NotFoundException("Booking 9 not found")).when(bookingService).delete(9L);

        // Act
        controller.deleteBooking(9L, ra);

        // Assert
        assertThat(ra.getFlashAttributes()).containsEntry("error", "Booking 9 not found");
    }
    @Test
    void updateRoom_roomNotFound_flashesNotFoundMessage() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        when(roomService.update(1L, roomDto)).thenThrow(new NotFoundException("Room 1 not found"));

        // Act
        String view = controller.updateRoom(1L, roomDto, noErrors(roomDto), ra);

        // Assert
        assertThat(view).isEqualTo("redirect:/rooms");
        assertThat(ra.getFlashAttributes()).containsEntry("error", "Room 1 not found");
    }

    @Test
    void deleteRoom_roomHasBookings_flashesGenericError() {
        // Arrange: БД відхиляє видалення через зовнішній ключ
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        doThrow(new DataIntegrityViolationException("fk")).when(roomService).delete(1L);

        // Act
        controller.deleteRoom(1L, ra);

        // Assert
        assertThat(ra.getFlashAttributes()).containsEntry("error", "Duplicate or invalid data");
    }

    @Test
    void updateBooking_serviceRejectsTimes_flashesServiceMessage() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        when(bookingService.update(1L, bookingDto))
                .thenThrow(new IllegalArgumentException("endTime must be after startTime"));

        // Act
        String view = controller.updateBooking(1L, bookingDto, noErrors(bookingDto), ra);

        // Assert
        assertThat(view).isEqualTo("redirect:/bookings");
        assertThat(ra.getFlashAttributes()).containsEntry("error", "endTime must be after startTime");
    }

    @Test
    void cancelBooking_bookingNotFound_flashesNotFoundMessage() {
        // Arrange
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        when(bookingService.cancel(9L)).thenThrow(new NotFoundException("Booking 9 not found"));

        // Act
        String view = controller.cancelBooking(9L, ra);

        // Assert
        assertThat(view).isEqualTo("redirect:/bookings");
        assertThat(ra.getFlashAttributes()).containsEntry("error", "Booking 9 not found");
    }
}
