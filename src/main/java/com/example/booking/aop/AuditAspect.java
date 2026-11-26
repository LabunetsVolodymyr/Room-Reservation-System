package com.example.booking.aop;

import com.example.booking.booking.BookingDto;
import com.example.booking.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Аспект для @Audited. Advice-тип @AfterReturning: виконується ЛИШЕ після успішного завершення методу
 * і має доступ до значення, що повертається (тут - новий статус бронювання).
 *
 * Pointcut "@annotation(audited)" вибирає методи з анотацією @Audited і одразу передає її екземпляр
 * у параметр audited - так читаємо назву дії audited.value().
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLog auditLog;
    private final CurrentUser currentUser;
    private final Clock clock;

    @AfterReturning(pointcut = "@annotation(audited)", returning = "result")
    public void audit(JoinPoint joinPoint, Audited audited, Object result) {
        Object[] args = joinPoint.getArgs();
        Object target = args.length > 0 ? args[0] : null;            // id бронювання / кімнати
        auditLog.record(new AuditEvent(
                clock.instant(),
                actor(),
                audited.value(),
                joinPoint.getSignature().toShortString(),
                target,
                describe(result)));
    }

    // Хто виконав дію. Без автентифікації (напр. виклик із фонової задачі) - "anonymous", аудит не падає
    private String actor() {
        try {
            return currentUser.id();
        } catch (RuntimeException e) {
            return "anonymous";
        }
    }

    // Короткий опис результату для журналу
    static String describe(Object result) {
        if (result instanceof BookingDto booking) {
            return "status=" + booking.status();
        }
        return result == null ? "OK" : result.getClass().getSimpleName();
    }
}
