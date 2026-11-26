package com.example.booking.aop;

import com.example.booking.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Аспект для @RateLimited. Advice-тип @Before: виконується ПЕРЕД методом. Сам @Before не може "пропустити"
 * метод, але може кинути виняток - тоді до методу виконання не дійде (це і є блокування).
 *
 * Алгоритм "ковзне вікно": для кожної пари (користувач, метод) зберігаємо час останніх викликів.
 * Перед викликом відкидаємо ті, що старші за вікно; якщо залишилось maxCalls - блокуємо.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final CurrentUser currentUser;
    private final Clock clock;

    // ключ "sub:Метод(..)" -> час дозволених викликів у поточному вікні (найстаріший першим)
    private final Map<String, Deque<Instant>> calls = new ConcurrentHashMap<>();

    @Before("@annotation(rateLimited)")
    public void checkLimit(JoinPoint joinPoint, RateLimited rateLimited) {
        String method = joinPoint.getSignature().toShortString();
        String key = currentUser.id() + ":" + method;
        Instant now = clock.instant();
        Instant windowStart = now.minusSeconds(rateLimited.perSeconds());

        Deque<Instant> history = calls.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (history) {                         // один користувач може слати паралельні запити
            while (!history.isEmpty() && !history.peekFirst().isAfter(windowStart)) {
                history.pollFirst();                     // виклик вийшов за межі вікна - більше не рахується
            }
            if (history.size() >= rateLimited.maxCalls()) {
                Instant freeAt = history.peekFirst().plusSeconds(rateLimited.perSeconds());
                long retryAfter = Math.max(1, Duration.between(now, freeAt).toSeconds());
                throw new RateLimitExceededException("Too many calls to " + method + ": limit is "
                        + rateLimited.maxCalls() + " per " + rateLimited.perSeconds() + " s, try again in "
                        + retryAfter + " s", retryAfter);
            }
            history.addLast(now);                        // виклик дозволено - запам'ятовуємо
        }
    }
}
