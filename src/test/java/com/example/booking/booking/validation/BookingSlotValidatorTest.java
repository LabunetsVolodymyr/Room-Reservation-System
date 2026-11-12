package com.example.booking.booking.validation;

import com.example.booking.booking.BookingDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Лаба 3, тип 1 (AC1-AC3). Використовується СПРАВЖНІЙ Bean Validation (Hibernate Validator), без Spring:
 * ми лише просимо validator.validate(dto), а BookingSlotValidator він знаходить сам за @Constraint(validatedBy).
 */
class BookingSlotValidatorTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 12, 1, 0, 0);

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    private static BookingDto slot(LocalDateTime start, LocalDateTime end) {
        return new BookingDto(null, 1L, null, null, start, end, null, null);
    }

    // Лише порушення нашого constraint-а (відфільтровуємо @NotNull тощо)
    private static Set<ConstraintViolation<BookingDto>> slotViolations(BookingDto dto) {
        Set<ConstraintViolation<BookingDto>> all = validator.validate(dto);
        all.removeIf(v -> v.getConstraintDescriptor().getAnnotation().annotationType() != ValidBookingSlot.class);
        return all;
    }

    // ================= AC1: валідатор знаходиться автоматично =================
    @Test
    void validate_dtoWithClassLevelAnnotation_bookingSlotValidatorIsDiscoveredAutomatically() {
        // Arrange: слот довший за 8 годин - порушення може дати лише наш BookingSlotValidator
        BookingDto dto = slot(DAY.withHour(8), DAY.withHour(18));

        // Act
        Set<ConstraintViolation<BookingDto>> violations = slotViolations(dto);

        // Assert
        assertThat(violations).hasSize(1);
        List<Class<?>> validators = List.copyOf(
                violations.iterator().next().getConstraintDescriptor().getConstraintValidatorClasses());
        assertThat(validators).containsExactly(BookingSlotValidator.class);
    }

    // ================= AC2: коректні дані - порушень немає =================
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "2026-12-01T10:00, 2026-12-01T11:00",   // звичайна година
            "2026-12-01T10:00, 2026-12-01T10:15",   // межа: рівно 15 хв
            "2026-12-01T09:00, 2026-12-01T17:00",   // межа: рівно 8 год
            "2026-12-01T18:00, 2026-12-01T23:59"    // пізно ввечері, але в той самий день
    })
    void validate_slotWithinRules_noViolations(LocalDateTime start, LocalDateTime end) {
        // Arrange
        BookingDto dto = slot(start, end);

        // Act
        Set<ConstraintViolation<BookingDto>> violations = slotViolations(dto);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void validate_missingTimes_constraintSkipsAndLeavesItToNotNull() {
        // Arrange
        BookingDto dto = slot(null, null);

        // Act
        Set<ConstraintViolation<BookingDto>> slot = slotViolations(dto);
        Set<ConstraintViolation<BookingDto>> all = validator.validate(dto);

        // Assert: наш constraint мовчить, а про відсутні поля каже @NotNull
        assertThat(slot).isEmpty();
        assertThat(all).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("startTime", "endTime");
    }

    // ================= AC3: порушення з чітким повідомленням =================
    @Test
    void validate_endBeforeStart_violationExplainsOrder() {
        // Arrange
        BookingDto dto = slot(DAY.withHour(11), DAY.withHour(10));

        // Act
        Set<ConstraintViolation<BookingDto>> violations = slotViolations(dto);

        // Assert
        assertThat(violations).singleElement().satisfies(v -> {
            assertThat(v.getPropertyPath().toString()).isEqualTo("endTime");
            assertThat(v.getMessage()).isEqualTo("Booking must end after it starts, but endTime "
                    + "2026-12-01T10:00 is not after startTime 2026-12-01T11:00");
        });
    }

    @Test
    void validate_endEqualsStart_violationExplainsOrder() {
        // Arrange: граничний випадок - нульова тривалість
        BookingDto dto = slot(DAY.withHour(10), DAY.withHour(10));

        // Act
        Set<ConstraintViolation<BookingDto>> violations = slotViolations(dto);

        // Assert
        assertThat(violations).singleElement().extracting(ConstraintViolation::getMessage)
                .asString().startsWith("Booking must end after it starts");
    }

    @Test
    void validate_slotOverMidnight_violationExplainsSameDayRule() {
        // Arrange: 23:00 -> 01:00 наступного дня (лише 2 години, але різні дні)
        BookingDto dto = slot(DAY.withHour(23), DAY.plusDays(1).withHour(1));

        // Act
        Set<ConstraintViolation<BookingDto>> violations = slotViolations(dto);

        // Assert
        assertThat(violations).singleElement().extracting(ConstraintViolation::getMessage)
                .isEqualTo("Booking must start and end on the same day, but it starts on 2026-12-01 "
                        + "and ends on 2026-12-02");
    }

    @Test
    void validate_slotShorterThan15Minutes_violationExplainsMinimum() {
        // Arrange: межа - 14 хвилин
        BookingDto dto = slot(DAY.withHour(10), DAY.withHour(10).plusMinutes(14));

        // Act
        Set<ConstraintViolation<BookingDto>> violations = slotViolations(dto);

        // Assert
        assertThat(violations).singleElement().extracting(ConstraintViolation::getMessage)
                .isEqualTo("Booking must last at least 15 minutes, but lasts only 14 minutes");
    }

    @Test
    void validate_slotLongerThan8Hours_violationExplainsMaximum() {
        // Arrange: межа - 8 год 1 хв
        BookingDto dto = slot(DAY.withHour(9), DAY.withHour(17).plusMinutes(1));

        // Act
        Set<ConstraintViolation<BookingDto>> violations = slotViolations(dto);

        // Assert
        assertThat(violations).singleElement().extracting(ConstraintViolation::getMessage)
                .isEqualTo("Booking must not last longer than 8 hours, but lasts 481 minutes");
    }
}
