package com.example.booking.room;

import com.example.booking.common.NotFoundException;
import lombok.RequiredArgsConstructor;                           // Lombok: конструктор для final-полів
import org.springframework.security.access.prepost.PreAuthorize; // перевірка прав ДО виконання методу
import org.springframework.stereotype.Service;                   // позначає бін бізнес-логіки
import org.springframework.transaction.annotation.Transactional; // керування транзакціями

import java.util.List;

/**
 * Service - шар бізнес-логіки. Тут правила, транзакції та перевірки доступу.
 *
 * @Transactional(readOnly = true) на рівні класу: усі методи за замовчуванням виконуються в транзакції лише
 * для читання (оптимізація Hibernate, захист від випадкових змін). Методи, що змінюють дані, перевизначають це
 * власним @Transactional.
 *
 * @RequiredArgsConstructor створює конструктор з усіма final-полями -> Spring впроваджує залежності через
 * конструктор (constructor injection): незмінні залежності, легко тестувати.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository repository; // доступ до БД
    private final RoomMapper mapper;         // Entity <-> DTO

    // Читання доступне будь-якому автентифікованому користувачу (його перевіряє SecurityFilterChain)
    public List<RoomDto> list() {
        return mapper.toDtoList(repository.findAll());
    }

    public RoomDto get(Long id) {
        return mapper.toDto(find(id));
    }

    // @PreAuthorize - ДРУГИЙ рівень захисту (перший - SecurityFilterChain). Spring створює проксі навколо
    // сервісу: перед викликом методу обчислює SpEL-вираз. Немає ролі -> AccessDeniedException -> 403.
    // Працює і для REST API, і для Thymeleaf UI, бо обидва викликають цей сервіс.
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional // читання+запис: перевизначає readOnly з класу
    public RoomDto create(RoomDto dto) {
        // saveAndFlush робить INSERT одразу, тож порушення UNIQUE (дубль назви) виявляється тут, а не при коміті
        return mapper.toDto(repository.saveAndFlush(mapper.toEntity(dto)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public RoomDto update(Long id, RoomDto dto) {
        Room room = find(id);          // завантажує сутність (стає "керованою" Hibernate)
        mapper.update(dto, room);      // копіює поля з DTO у цю сутність
        return mapper.toDto(repository.saveAndFlush(room));
    }

    @PreAuthorize("hasRole('ADMIN')") // видаляти кімнати може лише ADMIN
    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
        repository.flush(); // якщо є бронювання цієї кімнати - FK-помилка виникне тут -> 409
    }

    // Допоміжний метод: шукає кімнату або кидає NotFoundException (-> 404)
    private Room find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Room " + id + " not found"));
    }
}
