package com.example.booking.common;

import com.example.booking.security.CurrentUserIdArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** Налаштування Spring MVC: реєструємо власні резолвери аргументів контролерів. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        // Після цього параметри з @CurrentUserId у будь-якому контролері заповнюються автоматично
        resolvers.add(new CurrentUserIdArgumentResolver());
    }
}
