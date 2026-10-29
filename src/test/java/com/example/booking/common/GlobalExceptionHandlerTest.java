package com.example.booking.common;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit-тести перетворення винятків на HTTP-статуси (без Spring-контексту). */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFound_notFoundException_returns404WithMessage() {
        // Act
        ProblemDetail result = handler.notFound(new NotFoundException("Room 1 not found"));

        // Assert
        assertThat(result.getStatus()).isEqualTo(404);
        assertThat(result.getDetail()).isEqualTo("Room 1 not found");
    }

    @Test
    void badRequest_illegalArgument_returns400WithMessage() {
        // Act
        ProblemDetail result = handler.badRequest(new IllegalArgumentException("Room is not active"));

        // Assert
        assertThat(result.getStatus()).isEqualTo(400);
        assertThat(result.getDetail()).isEqualTo("Room is not active");
    }

    @Test
    void invalid_twoFieldErrors_joinsThemInOneMessage() {
        // Arrange
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("roomDto", "name", "must not be blank"),
                new FieldError("roomDto", "capacity", "must be at least 1")));

        // Act
        ProblemDetail result = handler.invalid(exception);

        // Assert
        assertThat(result.getStatus()).isEqualTo(400);
        assertThat(result.getDetail()).isEqualTo("name: must not be blank; capacity: must be at least 1");
    }

    @Test
    void invalid_noFieldErrors_returnsFallbackMessage() {
        // Arrange: межовий випадок - список помилок порожній
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());

        // Act
        ProblemDetail result = handler.invalid(exception);

        // Assert
        assertThat(result.getDetail()).isEqualTo("Validation failed");
    }

    @Test
    void conflict_illegalState_returns409WithOriginalMessage() {
        // Act
        ProblemDetail result = handler.conflict(new IllegalStateException("Only PENDING bookings can be edited"));

        // Assert
        assertThat(result.getStatus()).isEqualTo(409);
        assertThat(result.getDetail()).isEqualTo("Only PENDING bookings can be edited");
    }

    @Test
    void conflict_dataIntegrityViolation_returns409WithGenericMessage() {
        // Act
        ProblemDetail result = handler.conflict(new DataIntegrityViolationException("duplicate key ... SQL details"));

        // Assert: технічні деталі БД клієнту не показуються
        assertThat(result.getStatus()).isEqualTo(409);
        assertThat(result.getDetail()).isEqualTo("Duplicate or invalid data");
    }
}
