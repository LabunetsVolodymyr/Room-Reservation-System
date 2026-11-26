package com.example.booking.aop;

import com.example.booking.booking.BookingDto;
import com.example.booking.booking.BookingStatus;
import com.example.booking.security.CurrentUser;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.annotation.Annotation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Лаба 4: Unit-тест advice AuditAspect без Spring-контексту. JoinPoint - мок, аспект створюється через new,
 * годинник фіксований. Перевіряємо, ЩО саме потрапляє в журнал аудиту.
 */
@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    private static final Instant NOW = Instant.parse("2026-12-01T10:00:00Z");
    private static final LocalDateTime START = LocalDateTime.of(2026, 12, 1, 10, 0);

    @Mock
    private AuditLog auditLog;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private JoinPoint joinPoint;
    @Mock
    private Signature signature;

    private AuditAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new AuditAspect(auditLog, currentUser, Clock.fixed(NOW, ZoneOffset.UTC));
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("BookingService.confirm(..)");
    }

    // Екземпляр анотації для передачі в advice (так само, як його передає Spring через @annotation(audited))
    private static Audited audited(String action) {
        return new Audited() {
            @Override
            public String value() {
                return action;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Audited.class;
            }
        };
    }

    @Test
    void audit_successfulConfirm_recordsActorActionTargetAndNewStatus() {
        // Arrange
        when(joinPoint.getArgs()).thenReturn(new Object[]{5L});
        when(currentUser.id()).thenReturn("manager-1");
        BookingDto result = new BookingDto(5L, 1L, "Alpha", "user-1", START, START.plusHours(1),
                BookingStatus.CONFIRMED, START);

        // Act
        aspect.audit(joinPoint, audited("BOOKING_CONFIRMED"), result);

        // Assert
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLog).record(event.capture());
        assertThat(event.getValue()).isEqualTo(new AuditEvent(NOW, "manager-1", "BOOKING_CONFIRMED",
                "BookingService.confirm(..)", 5L, "status=CONFIRMED"));
    }

    @Test
    void audit_voidMethod_recordsOkOutcome() {
        // Arrange: delete(...) повертає void -> result == null
        when(joinPoint.getArgs()).thenReturn(new Object[]{7L});
        when(currentUser.id()).thenReturn("user-1");

        // Act
        aspect.audit(joinPoint, audited("BOOKING_DELETED"), null);

        // Assert
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLog).record(event.capture());
        assertThat(event.getValue().outcome()).isEqualTo("OK");
        assertThat(event.getValue().target()).isEqualTo(7L);
    }

    @Test
    void audit_noAuthenticatedUser_recordsAnonymousActorInsteadOfFailing() {
        // Arrange: CurrentUser не може визначити користувача (немає Authentication)
        when(joinPoint.getArgs()).thenReturn(new Object[]{5L});
        when(currentUser.id()).thenThrow(new IllegalStateException("Unsupported principal: null"));

        // Act
        aspect.audit(joinPoint, audited("BOOKING_CANCELLED"), null);

        // Assert
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLog).record(event.capture());
        assertThat(event.getValue().actor()).isEqualTo("anonymous");
    }

    @Test
    void audit_methodWithoutArguments_recordsNullTarget() {
        // Arrange: граничний випадок - метод без аргументів
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(currentUser.id()).thenReturn("admin-1");

        // Act
        aspect.audit(joinPoint, audited("SOMETHING"), "text");

        // Assert
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLog).record(event.capture());
        assertThat(event.getValue().target()).isNull();
        assertThat(event.getValue().outcome()).isEqualTo("String");
    }
}
