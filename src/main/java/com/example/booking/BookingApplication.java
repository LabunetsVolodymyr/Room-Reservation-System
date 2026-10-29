// Кореневий пакет застосунку. Spring сканує цей пакет і ВСІ вкладені (room, booking, security, web, common),
// шукаючи класи з анотаціями @Component/@Service/@Controller/@Configuration тощо.
package com.example.booking;

import org.springframework.boot.SpringApplication;                       // клас, що запускає застосунок
import org.springframework.boot.autoconfigure.SpringBootApplication;     // мета-анотація, див. нижче

/**
 * Точка входу.
 *
 * @SpringBootApplication = три анотації разом:
 *  1) @Configuration         - клас може оголошувати бінси (@Bean);
 *  2) @EnableAutoConfiguration - Spring Boot сам налаштовує DataSource, JPA, Liquibase, Security, Thymeleaf
 *                              на основі залежностей у pom.xml та application.yml;
 *  3) @ComponentScan         - автоматично знаходить наші компоненти у пакеті com.example.booking.
 */
@SpringBootApplication
public class BookingApplication {

    public static void main(String[] args) {
        // Створює Spring-контекст (контейнер бінів), піднімає вбудований Tomcat на порту з application.yml (8080).
        SpringApplication.run(BookingApplication.class, args);
    }
}
