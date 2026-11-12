package com.example.booking.booking.validation;

import com.example.booking.booking.BookingDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Валідатор для @ValidBookingSlot. Екземпляр створює Bean Validation (через reflection) і викликає
 * initialize(...) з параметрами анотації, а потім isValid(...) для кожного DTO.
 */
public class BookingSlotValidator implements ConstraintValidator<ValidBookingSlot, BookingDto> {

    private Duration minDuration;
    private Duration maxDuration;

    @Override
    public void initialize(ValidBookingSlot annotation) {
        // Значення елементів анотації (minMinutes/maxHours) доступні тут
        minDuration = Duration.ofMinutes(annotation.minMinutes());
        maxDuration = Duration.ofHours(annotation.maxHours());
    }

    @Override
    public boolean isValid(BookingDto dto, ConstraintValidatorContext context) {
        // null-поля - відповідальність @NotNull. Цей constraint перевіряє лише правило про інтервал.
        if (dto == null || dto.startTime() == null || dto.endTime() == null) {
            return true;
        }
        String error = findError(dto.startTime(), dto.endTime());
        if (error == null) {
            return true;
        }
        // Замість загального message() віддаємо конкретне пояснення і прив'язуємо його до поля endTime:
        // у відповіді API це виглядає як "endTime: Booking must ..."
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(error)
                .addPropertyNode("endTime")
                .addConstraintViolation();
        return false;
    }

    // Повертає текст порушення або null, якщо слот коректний
    private String findError(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            return "Booking must end after it starts, but endTime " + end + " is not after startTime " + start;
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            return "Booking must start and end on the same day, but it starts on " + start.toLocalDate()
                    + " and ends on " + end.toLocalDate();
        }
        Duration duration = Duration.between(start, end);
        if (duration.compareTo(minDuration) < 0) {
            return "Booking must last at least " + minDuration.toMinutes() + " minutes, but lasts only "
                    + duration.toMinutes() + " minutes";
        }
        if (duration.compareTo(maxDuration) > 0) {
            return "Booking must not last longer than " + maxDuration.toHours() + " hours, but lasts "
                    + duration.toMinutes() + " minutes";
        }
        return null;
    }
}
