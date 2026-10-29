package com.example.booking.common;

import org.springframework.dao.DataIntegrityViolationException;   // порушення обмежень БД (unique, FK)
import org.springframework.http.HttpStatus;                       // enum HTTP-статусів
import org.springframework.http.ProblemDetail;                    // стандартний формат помилки RFC 7807 (Spring 6+)
import org.springframework.web.bind.MethodArgumentNotValidException; // кидається, коли @Valid не пройшла
import org.springframework.web.bind.annotation.ExceptionHandler;  // позначає метод-обробник винятку
import org.springframework.web.bind.annotation.RestControllerAdvice; // глобальний обробник для контролерів
import org.springframework.web.bind.annotation.RestController;

/**
 * Централізована обробка помилок для REST API: перетворює винятки на правильні HTTP-статуси.
 *
 * annotations = RestController.class - діє ЛИШЕ на @RestController (API). Thymeleaf-контролер
 * (@Controller) має власну обробку помилок, тому ми його не зачіпаємо.
 *
 * AccessDeniedException тут навмисно НЕ перехоплюється: його обробляє Spring Security
 * (ExceptionTranslationFilter), який повертає 403. Якби ми зловили його тут, клієнт отримав би 500 або 200.
 */
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    // 404 Not Found: сутність із таким id не існує
    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    // 400 Bad Request: некоректні дані (наприклад, endTime раніше за startTime, неактивна кімната)
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // 400 Bad Request: не пройшла Bean Validation (@NotBlank, @Min, @NotNull у DTO)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        // Збираємо всі помилки полів у один рядок: "name: must not be blank; capacity: must be >= 1"
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b).orElse("Validation failed");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, msg);
    }

    // 409 Conflict: конфлікт стану (редагування не-PENDING бронювання) або порушення обмеження БД (дубль назви кімнати)
    @ExceptionHandler({IllegalStateException.class, DataIntegrityViolationException.class})
    ProblemDetail conflict(Exception e) {
        // Для винятку БД не показуємо клієнту технічні деталі SQL, лише загальне повідомлення
        String msg = e instanceof IllegalStateException ? e.getMessage() : "Duplicate or invalid data";
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, msg);
    }
}
