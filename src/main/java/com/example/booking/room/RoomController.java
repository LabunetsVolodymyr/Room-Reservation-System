package com.example.booking.room;

import jakarta.validation.Valid;                        // запускає перевірку анотацій валідації DTO
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;        // дозволяє керувати статусом і заголовками відповіді
import org.springframework.web.bind.annotation.*;      // @RestController, @GetMapping, @PathVariable тощо

import java.net.URI;
import java.util.List;

/**
 * REST-контролер: приймає HTTP-запити, делегує сервісу, повертає JSON. Бізнес-логіки тут немає.
 * @RestController = @Controller + @ResponseBody: значення, що повертаються, серіалізуються в JSON (Jackson).
 * Шлях /api/** обробляє перший SecurityFilterChain (JWT), див. SecurityConfig.
 */
@RestController
@RequestMapping("/api/rooms")   // спільний префікс для всіх методів класу
@RequiredArgsConstructor
public class RoomController {

    private final RoomService service;

    // GET /api/rooms -> 200 OK + масив кімнат
    @GetMapping
    public List<RoomDto> list() {
        return service.list();
    }

    // GET /api/rooms/5 -> 200 OK або 404. @PathVariable бере {id} із URL
    @GetMapping("/{id}")
    public RoomDto get(@PathVariable Long id) {
        return service.get(id);
    }

    // POST /api/rooms -> 201 Created. @RequestBody перетворює JSON у RoomDto, @Valid перевіряє поля (інакше 400)
    @PostMapping
    public ResponseEntity<RoomDto> create(@Valid @RequestBody RoomDto dto) {
        RoomDto created = service.create(dto);
        // 201 Created + заголовок Location з адресою нового ресурсу (REST-практика)
        return ResponseEntity.created(URI.create("/api/rooms/" + created.id())).body(created);
    }

    // PUT /api/rooms/5 -> 200 OK: повне оновлення кімнати
    @PutMapping("/{id}")
    public RoomDto update(@PathVariable Long id, @Valid @RequestBody RoomDto dto) {
        return service.update(id, dto);
    }

    // DELETE /api/rooms/5 -> 204 No Content (успіх без тіла відповіді)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
