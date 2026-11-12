package com.example.booking.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Лаба 3, тип 2 - композитна (meta-) анотація.
 *
 * У сервісах ці дві анотації регулярно стояли РАЗОМ на кожному методі, що змінює дані і доступний лише
 * ADMIN/MANAGER (RoomService.create, RoomService.update, BookingService.confirm):
 *
 *     @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
 *     @Transactional
 *
 * Тепер достатньо написати @PrivilegedWrite. Це той самий патерн, що й @RestController
 * (= @Controller + @ResponseBody) чи @SpringBootApplication: Spring шукає анотації не лише безпосередньо
 * на методі, а й на анотаціях, якими позначено інші анотації (merged / meta-annotations). Тому:
 *  - Spring Security знаходить @PreAuthorize і перед викликом перевіряє роль (немає -> AccessDeniedException);
 *  - Spring TX знаходить @Transactional і відкриває транзакцію для читання+запису
 *    (перевизначає @Transactional(readOnly = true) на рівні класу сервісу).
 *
 * @Retention(RUNTIME) обов'язковий: Spring читає анотацію через reflection під час виконання.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@Transactional
public @interface PrivilegedWrite {
}
