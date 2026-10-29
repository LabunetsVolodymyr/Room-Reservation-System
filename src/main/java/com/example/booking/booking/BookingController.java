package com.example.booking.booking;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** REST API бронювань (/api/bookings). Тонкий контролер: усе вирішує BookingService. Автентифікація - JWT. */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService service;

    // GET /api/bookings -> список (USER: свої; ADMIN/MANAGER: усі)
    @GetMapping
    public List<BookingDto> list() {
        return service.list();
    }

    // GET /api/bookings/5
    @GetMapping("/{id}")
    public BookingDto get(@PathVariable Long id) {
        return service.get(id);
    }

    // POST /api/bookings -> 201 Created + Location
    @PostMapping
    public ResponseEntity<BookingDto> create(@Valid @RequestBody BookingDto dto) {
        BookingDto created = service.create(dto);
        return ResponseEntity.created(URI.create("/api/bookings/" + created.id())).body(created);
    }

    // PUT /api/bookings/5 -> змінити час/кімнату (лише PENDING)
    @PutMapping("/{id}")
    public BookingDto update(@PathVariable Long id, @Valid @RequestBody BookingDto dto) {
        return service.update(id, dto);
    }

    // DELETE /api/bookings/5 -> 204 No Content
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // POST /api/bookings/5/confirm - дія над ресурсом (не CRUD), тому POST на підресурс.
    // Тут є лише @PreAuthorize у сервісі: демонстрація другого рівня захисту.
    @PostMapping("/{id}/confirm")
    public BookingDto confirm(@PathVariable Long id) {
        return service.confirm(id);
    }

    // POST /api/bookings/5/cancel
    @PostMapping("/{id}/cancel")
    public BookingDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
