package com.example.booking.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Годинник як бін: аспекти беруть час із Clock, а не з Instant.now(). У тестах підставляємо фіксований
 * або керований годинник - тести детерміновані (без Thread.sleep і реального часу).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
