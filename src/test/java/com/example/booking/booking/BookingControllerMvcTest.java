package com.example.booking.booking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Лаба 3: справжні HTTP-запити до BookingController через зріз Spring MVC (@WebMvcTest, як у лекційному
 * ProductPublicEndpointsWebMvcTest). Піднімається лише веб-шар: DispatcherServlet, routing, Jackson,
 * Bean Validation, GlobalExceptionHandler і WebMvcConfig - тобто резолвер @CurrentUserId реєструється
 * САМИМ застосунком, а не вручну в тесті. БД, Keycloak і сервіси не створюються; BookingService - мок.
 *
 * OAuth2-автоконфігурації вимкнено (вони потребують Keycloak на старті), фільтри безпеки теж
 * (addFilters = false): ролі тут не перевіряються, а користувача передаємо через .principal(...).
 *
 *  - AC8: значення з @CurrentUserId реально використовується в тілі методу (передається в сервіс).
 *  - Тип 1 через реальний запит: @ValidBookingSlot спрацьовує на @Valid @RequestBody -> 400 з поясненням.
 */
@WebMvcTest(
        controllers = BookingController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        }
)
@AutoConfigureMockMvc(addFilters = false)
class BookingControllerMvcTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 12, 1, 10, 0);

    @MockitoBean
    private BookingService service;

    @Autowired
    private MockMvc mvc;

    private static JwtAuthenticationToken userWithSub(String sub) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(sub).build();
        return new JwtAuthenticationToken(jwt);
    }

    // ================= AC8 =================
    @Test
    void mine_authenticatedRequest_passesResolvedUserIdToServiceAndReturnsItsBookings() throws Exception {
        // Arrange
        BookingDto own = new BookingDto(7L, 1L, "Alpha", "user-1", START, START.plusHours(1),
                BookingStatus.PENDING, START);
        when(service.listByOwner("user-1")).thenReturn(List.of(own));

        // Act + Assert (MockMvc: perform - дія, andExpect - перевірки відповіді)
        mvc.perform(get("/api/bookings/mine").principal(userWithSub("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].ownerId").value("user-1"));

        // Assert: саме резолвлений sub потрапив у сервіс
        verify(service).listByOwner("user-1");
    }

    @Test
    void mine_differentUser_serviceReceivesThatUsersId() throws Exception {
        // Arrange
        when(service.listByOwner("user-2")).thenReturn(List.of());

        // Act
        mvc.perform(get("/api/bookings/mine").principal(userWithSub("user-2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Assert
        verify(service).listByOwner("user-2");
    }

    // ================= Тип 1 через реальний запит =================
    @Test
    void create_slotLongerThan8Hours_returns400WithExplanationAndSkipsService() throws Exception {
        // Arrange
        String body = """
                {"roomId": 1, "startTime": "2026-12-01T08:00:00", "endTime": "2026-12-01T18:00:00"}
                """;

        // Act + Assert
        mvc.perform(post("/api/bookings").principal(userWithSub("user-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("endTime: Booking must not last longer than 8 hours, but lasts 600 minutes"));

        // Assert
        verify(service, never()).create(any());
    }

    @Test
    void create_validSlot_reachesServiceAndReturns201() throws Exception {
        // Arrange
        String body = """
                {"roomId": 1, "startTime": "2026-12-01T10:00:00", "endTime": "2026-12-01T11:00:00"}
                """;
        when(service.create(any())).thenReturn(new BookingDto(3L, 1L, "Alpha", "user-1", START,
                START.plusHours(1), BookingStatus.PENDING, START));

        // Act + Assert
        mvc.perform(post("/api/bookings").principal(userWithSub("user-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3));
    }
}
