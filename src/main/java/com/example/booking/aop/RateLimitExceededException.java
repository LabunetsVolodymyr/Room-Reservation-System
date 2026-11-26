package com.example.booking.aop;

/**
 * Користувач перевищив ліміт викликів @RateLimited.
 * REST API: GlobalExceptionHandler -> 429 Too Many Requests + заголовок Retry-After.
 * UI: наслідує IllegalStateException, тому WebController показує повідомлення у flash, а не сторінку помилки.
 */
public class RateLimitExceededException extends IllegalStateException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** Через скільки секунд можна повторити запит. */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
