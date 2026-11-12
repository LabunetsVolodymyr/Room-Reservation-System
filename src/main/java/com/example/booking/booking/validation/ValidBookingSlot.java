package com.example.booking.booking.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Лаба 3, тип 1 - власна Bean Validation constraint-анотація рівня КЛАСУ.
 *
 * Бізнес-правило "коректний часовий слот бронювання" стосується ДВОХ полів одночасно (startTime і endTime),
 * тому анотація ставиться на весь DTO, а не на окреме поле:
 *  1) endTime має бути пізніше за startTime;
 *  2) бронювання починається і закінчується в один календарний день;
 *  3) тривалість - від minMinutes() до maxHours() (за замовчуванням 15 хв ... 8 год).
 *
 * @Constraint(validatedBy = ...) - саме за цим посиланням Bean Validation (Hibernate Validator) сам знаходить
 * і створює BookingSlotValidator. Ніде в коді застосунку "new BookingSlotValidator()" не викликається.
 * @Retention(RUNTIME) - анотація має бути видимою через reflection під час виконання, інакше валідатор її не знайде.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BookingSlotValidator.class)
public @interface ValidBookingSlot {

    // Повідомлення за замовчуванням. Валідатор замінює його конкретним поясненням, ЩО саме порушено.
    String message() default "Booking time slot breaks the booking rules";

    // Обов'язкові елементи будь-якої constraint-анотації (вимога специфікації Bean Validation)
    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Мінімальна тривалість бронювання у хвилинах. */
    int minMinutes() default 15;

    /** Максимальна тривалість бронювання у годинах. */
    int maxHours() default 8;
}
