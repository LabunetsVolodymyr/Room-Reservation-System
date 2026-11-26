package com.example.booking.aop;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Журнал аудиту: пише кожну подію в лог (логер "AUDIT") і тримає останні MAX_EVENTS у пам'яті.
 * Окремий бін, щоб AuditAspect можна було тестувати: у тесті перевіряємо, ЩО саме записано.
 */
@Slf4j(topic = "AUDIT")
@Component
public class AuditLog {

    static final int MAX_EVENTS = 200;

    // Потокобезпечна черга: нові події на початку, найстаріші видаляються з кінця
    private final Deque<AuditEvent> events = new ConcurrentLinkedDeque<>();

    public void record(AuditEvent event) {
        log.info("{} by {} on {} [{}] -> {}",
                event.action(), event.actor(), event.target(), event.method(), event.outcome());
        events.addFirst(event);
        while (events.size() > MAX_EVENTS) {
            events.pollLast();
        }
    }

    /** Останні події, найновіші першими. */
    public List<AuditEvent> recent() {
        return List.copyOf(events);
    }
}
