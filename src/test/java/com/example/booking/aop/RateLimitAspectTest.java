package com.example.booking.aop;

import com.example.booking.security.CurrentUser;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.annotation.Annotation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Лаба 4: Unit-тест advice RateLimitAspect без Spring. Час керований (MutableClock) - замість Thread.sleep
 * просто "переводимо годинник" вперед, тест детермінований.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    private static final RateLimited THREE_PER_MINUTE = limit(3, 60);

    @Mock
    private CurrentUser currentUser;
    @Mock
    private JoinPoint joinPoint;
    @Mock
    private Signature signature;

    private MutableClock clock;
    private RateLimitAspect aspect;

    /** Годинник, який тест може переводити вперед. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static RateLimited limit(int maxCalls, int perSeconds) {
        return new RateLimited() {
            @Override
            public int maxCalls() {
                return maxCalls;
            }

            @Override
            public int perSeconds() {
                return perSeconds;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return RateLimited.class;
            }
        };
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-12-01T10:00:00Z"));
        aspect = new RateLimitAspect(currentUser, clock);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("BookingService.create(..)");
        lenient().when(currentUser.id()).thenReturn("user-1");
    }

    // Викликає advice n разів поспіль
    private void callTimes(int n) {
        for (int i = 0; i < n; i++) {
            aspect.checkLimit(joinPoint, THREE_PER_MINUTE);
        }
    }

    @Test
    void checkLimit_callsWithinLimit_areAllowed() {
        // Act: рівно 3 виклики при ліміті 3
        Throwable thrown = catchThrowable(() -> callTimes(3));

        // Assert
        assertThat(thrown).isNull();
    }

    @Test
    void checkLimit_callOverLimit_throwsWithRetryAfter() {
        // Arrange
        callTimes(3);
        clock.advance(Duration.ofSeconds(20));

        // Act: 4-й виклик у тому самому вікні
        Throwable thrown = catchThrowable(() -> aspect.checkLimit(joinPoint, THREE_PER_MINUTE));

        // Assert: найстаріший виклик звільниться через 60 - 20 = 40 с
        assertThat(thrown).isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Too many calls to BookingService.create(..): limit is 3 per 60 s, try again in 40 s");
        assertThat(((RateLimitExceededException) thrown).getRetryAfterSeconds()).isEqualTo(40);
    }

    @Test
    void checkLimit_afterWindowPasses_callsAreAllowedAgain() {
        // Arrange
        callTimes(3);
        clock.advance(Duration.ofSeconds(60));   // межа: рівно довжина вікна - старі виклики вже не рахуються

        // Act
        Throwable thrown = catchThrowable(() -> callTimes(3));

        // Assert
        assertThat(thrown).isNull();
    }

    @Test
    void checkLimit_rejectedCall_isNotCountedInWindow() {
        // Arrange: 3 дозволені + 1 відхилений
        callTimes(3);
        catchThrowable(() -> aspect.checkLimit(joinPoint, THREE_PER_MINUTE));
        clock.advance(Duration.ofSeconds(60));

        // Act: вікно минуло - відхилений виклик не мав "заблокувати" нові
        Throwable thrown = catchThrowable(() -> callTimes(3));

        // Assert
        assertThat(thrown).isNull();
    }

    @Test
    void checkLimit_differentUsers_haveSeparateLimits() {
        // Arrange: user-1 вичерпав ліміт
        callTimes(3);
        when(currentUser.id()).thenReturn("user-2");

        // Act
        Throwable thrown = catchThrowable(() -> aspect.checkLimit(joinPoint, THREE_PER_MINUTE));

        // Assert
        assertThat(thrown).isNull();
    }

    @Test
    void checkLimit_differentMethods_haveSeparateLimits() {
        // Arrange: ліміт вичерпано для create(..)
        callTimes(3);
        when(signature.toShortString()).thenReturn("BookingService.update(..)");

        // Act
        Throwable thrown = catchThrowable(() -> aspect.checkLimit(joinPoint, THREE_PER_MINUTE));

        // Assert
        assertThat(thrown).isNull();
    }
}
