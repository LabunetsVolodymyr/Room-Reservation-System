package com.example.booking.web;

import com.example.booking.booking.BookingDto;
import com.example.booking.booking.BookingService;
import com.example.booking.common.NotFoundException;
import com.example.booking.room.RoomDto;
import com.example.booking.room.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;                 // MVC-контролер: повертає ІМЕНА шаблонів
import org.springframework.ui.Model;                              // дані для шаблону
import org.springframework.validation.BindingResult;              // результат валідації форми
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // flash-повідомлення між редіректами

/**
 * Простий Thymeleaf UI. Працює через сесію (OIDC), викликає ті самі сервіси, що й REST API.
 *
 * Теорія: на відміну від @RestController, тут @Controller - метод повертає назву шаблону (наприклад "rooms" ->
 * templates/rooms.html) або "redirect:/url". Патерн PRG (Post-Redirect-Get): після POST робимо редірект, щоб
 * оновлення сторінки (F5) не повторювало форму.
 */
@Controller
@RequiredArgsConstructor
public class WebController {

    private final RoomService roomService;
    private final BookingService bookingService;

    // Головна сторінка перенаправляє на список кімнат. Доступ до "/" вимагає логіну -> для гостя спрацює OIDC-редирект
    @GetMapping("/")
    String home() {
        return "redirect:/rooms";
    }

    // Сторінка лише для ADMIN. Обмеження стоїть у SecurityConfig (/admin/** -> hasRole ADMIN), а не тут
    @GetMapping("/admin")
    String admin() {
        return "admin"; // шаблон templates/admin.html
    }

    // ----- rooms -----
    // GET /rooms: кладемо список кімнат у модель, шаблон rooms.html його відображає
    @GetMapping("/rooms")
    String rooms(Model model) {
        model.addAttribute("rooms", roomService.list());
        return "rooms";
    }

    // POST /rooms: форма створення. @ModelAttribute збирає поля форми у RoomDto, @Valid перевіряє,
    // BindingResult збирає помилки (без нього помилка валідації кинула б виняток)
    @PostMapping("/rooms")
    String createRoom(@Valid @ModelAttribute RoomDto dto, BindingResult errors, RedirectAttributes ra) {
        if (errors.hasErrors()) {
            ra.addFlashAttribute("error", "Invalid room data");
            return "redirect:/rooms";
        }
        return run(ra, "/rooms", () -> roomService.create(dto), "Room created");
    }

    // POST /rooms/5: оновлення з рядка таблиці
    @PostMapping("/rooms/{id}")
    String updateRoom(@PathVariable Long id, @Valid @ModelAttribute RoomDto dto,
                      BindingResult errors, RedirectAttributes ra) {
        if (errors.hasErrors()) {
            ra.addFlashAttribute("error", "Invalid room data");
            return "redirect:/rooms";
        }
        return run(ra, "/rooms", () -> roomService.update(id, dto), "Room updated");
    }

    // POST /rooms/5/delete: HTML-форми вміють лише GET/POST, тому видалення - це POST на окремий URL
    @PostMapping("/rooms/{id}/delete")
    String deleteRoom(@PathVariable Long id, RedirectAttributes ra) {
        return run(ra, "/rooms", () -> roomService.delete(id), "Room deleted");
    }

    // ----- bookings -----
    // Сторінка бронювань: передаємо і бронювання, і кімнати (для випадаючого списку у формі)
    @GetMapping("/bookings")
    String bookings(Model model) {
        model.addAttribute("bookings", bookingService.list());
        model.addAttribute("rooms", roomService.list());
        return "bookings";
    }

    @PostMapping("/bookings")
    String createBooking(@Valid @ModelAttribute BookingDto dto, BindingResult errors, RedirectAttributes ra) {
        if (errors.hasErrors()) {
            ra.addFlashAttribute("error", "Invalid booking data");
            return "redirect:/bookings";
        }
        return run(ra, "/bookings", () -> bookingService.create(dto), "Booking created");
    }

    @PostMapping("/bookings/{id}")
    String updateBooking(@PathVariable Long id, @Valid @ModelAttribute BookingDto dto,
                         BindingResult errors, RedirectAttributes ra) {
        if (errors.hasErrors()) {
            ra.addFlashAttribute("error", "Invalid booking data");
            return "redirect:/bookings";
        }
        return run(ra, "/bookings", () -> bookingService.update(id, dto), "Booking updated");
    }

    // Для USER виклик confirm заблокує @PreAuthorize у сервісі -> AccessDeniedException -> сторінка 403
    @PostMapping("/bookings/{id}/confirm")
    String confirmBooking(@PathVariable Long id, RedirectAttributes ra) {
        return run(ra, "/bookings", () -> bookingService.confirm(id), "Booking confirmed");
    }

    @PostMapping("/bookings/{id}/cancel")
    String cancelBooking(@PathVariable Long id, RedirectAttributes ra) {
        return run(ra, "/bookings", () -> bookingService.cancel(id), "Booking cancelled");
    }

    @PostMapping("/bookings/{id}/delete")
    String deleteBooking(@PathVariable Long id, RedirectAttributes ra) {
        return run(ra, "/bookings", () -> bookingService.delete(id), "Booking deleted");
    }

    /** AccessDeniedException свідомо не ловимо: Spring Security покаже 403. */
    // Спільний допоміжний метод: виконує дію, а бізнес-помилки перетворює на повідомлення для користувача.
    // Runnable - це "дія без аргументів і результату" (лямбда). flash-атрибути живуть до наступного запиту (після редіректу).
    private String run(RedirectAttributes ra, String back, Runnable action, String okMessage) {
        try {
            action.run();
            ra.addFlashAttribute("msg", okMessage);
        } catch (IllegalArgumentException | IllegalStateException | NotFoundException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "Duplicate or invalid data");
        }
        return "redirect:" + back;
    }
}
