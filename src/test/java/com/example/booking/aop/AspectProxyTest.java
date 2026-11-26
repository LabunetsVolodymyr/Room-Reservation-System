package com.example.booking.aop;

import com.example.booking.booking.Booking;
import com.example.booking.booking.BookingDto;
import com.example.booking.booking.BookingMapper;
import com.example.booking.booking.BookingRepository;
import com.example.booking.booking.BookingService;
import com.example.booking.booking.BookingStatus;
import com.example.booking.room.Room;
import com.example.booking.room.RoomDto;
import com.example.booking.room.RoomMapper;
import com.example.booking.room.RoomRepository;
import com.example.booking.room.RoomService;
import com.example.booking.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Лаба 4: аспекти через СПРАВЖНІЙ AOP-проксі Spring - зовнішній виклик vs самовиклик (self-invocation).
 *
 * Мінімальний контекст: @EnableAspectJAutoProxy + три аспекти + сервіси. Репозиторії і CurrentUser - моки,
 * годинник фіксований; БД, вебсервера і Keycloak немає. (@Transactional і @PreAuthorize тут неактивні:
 * транзакції й method security не ввімкнені - тестуємо лише наші аспекти.)
 */
@SpringJUnitConfig(AspectProxyTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // свіжі AuditLog і лічильники rate limit
class AspectProxyTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 12, 1, 10, 0);

    @Configuration
    @EnableAspectJAutoProxy
    static class Config {
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-12-01T09:00:00Z"), ZoneOffset.UTC); }
        @Bean CurrentUser currentUser() { return mock(CurrentUser.class); }
        @Bean BookingRepository bookingRepository() { return mock(BookingRepository.class); }
        @Bean RoomRepository roomRepository() { return mock(RoomRepository.class); }
        @Bean AuditLog auditLog() { return new AuditLog(); }
        @Bean AuditAspect auditAspect(AuditLog log, CurrentUser user, Clock clock) { return new AuditAspect(log, user, clock); }
        @Bean RateLimitAspect rateLimitAspect(CurrentUser user, Clock clock) { return new RateLimitAspect(user, clock); }
        @Bean InputNormalizationAspect inputNormalizationAspect() { return new InputNormalizationAspect(); }

        @Bean
        BookingService bookingService(BookingRepository bookings, RoomRepository rooms, CurrentUser user) {
            return new BookingService(bookings, rooms, Mappers.getMapper(BookingMapper.class), user);
        }

        @Bean
        RoomService roomService(RoomRepository rooms) {
            return new RoomService(rooms, Mappers.getMapper(RoomMapper.class));
        }
    }

    @Autowired private BookingService bookingService;   // це ПРОКСІ, а не сам BookingService
    @Autowired private RoomService roomService;
    @Autowired private AuditLog auditLog;
    @Autowired private CurrentUser currentUser;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private RoomRepository roomRepository;

    private Booking pending;

    @BeforeEach
    void setUp() {
        reset(currentUser, bookingRepository, roomRepository);
        when(currentUser.id()).thenReturn("user-1");
        when(currentUser.isPrivileged()).thenReturn(false);

        Room room = new Room();
        room.setId(1L);
        room.setName("Alpha");
        room.setActive(true);
        pending = new Booking();
        pending.setId(5L);
        pending.setRoom(room);
        pending.setOwnerId("user-1");
        pending.setStartTime(START);
        pending.setEndTime(START.plusHours(1));
        pending.setStatus(BookingStatus.PENDING);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(pending));
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roomRepository.saveAndFlush(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void injectedService_isCglibProxy() {
        // Act + Assert: Spring Boot за замовчуванням створює CGLIB-проксі (підклас), BookingService без інтерфейсу
        assertThat(AopUtils.isCglibProxy(bookingService)).isTrue();
    }

    // ================= @Audited: зовнішній виклик vs самовиклик =================
    @Test
    void cancel_calledFromOutside_goesThroughProxyAndIsAudited() {
        // Act: виклик ззовні класу (з тесту) -> через проксі
        BookingDto result = bookingService.cancel(5L);

        // Assert: метод виконався І advice спрацював
        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(auditLog.recent()).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo("BOOKING_CANCELLED");
            assertThat(event.actor()).isEqualTo("user-1");
            assertThat(event.target()).isEqualTo(5L);
            assertThat(event.outcome()).isEqualTo("status=CANCELLED");
        });
    }

    @Test
    void cancel_calledViaThisInsideService_bypassesProxyAndIsNotAudited() {
        // Act: той самий cancel(), але через this.cancel(...) всередині BookingService (самовиклик)
        BookingDto result = bookingService.cancelViaSelfInvocation(5L);

        // Assert: бронювання скасовано (метод виконався), але журнал аудиту ПОРОЖНІЙ - і жодної помилки
        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(pending.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(auditLog.recent()).isEmpty();
    }

    @Test
    void cancel_targetMethodFails_isNotAudited() {
        // Arrange: чуже бронювання -> AccessDeniedException у методі
        pending.setOwnerId("someone-else");

        // Act
        Throwable thrown = catchThrowable(() -> bookingService.cancel(5L));

        // Assert: @AfterReturning спрацьовує лише після УСПІШНОГО методу
        assertThat(thrown).isNotNull();
        assertThat(auditLog.recent()).isEmpty();
    }

    // ================= @RateLimited через проксі =================
    @Test
    void create_moreThanFiveTimesPerMinute_sixthCallIsBlockedBeforeMethodRuns() {
        // Arrange
        BookingDto request = new BookingDto(null, 1L, null, null, START, START.plusHours(1), null, null);
        for (int i = 0; i < 5; i++) {
            bookingService.create(request);
        }

        // Act
        Throwable thrown = catchThrowable(() -> bookingService.create(request));

        // Assert: 6-й виклик заблоковано, до save() справа не дійшла (5 збережень, не 6)
        assertThat(thrown).isInstanceOf(RateLimitExceededException.class);
        verify(bookingRepository, times(5)).save(any(Booking.class));
    }

    // ================= @NormalizedInput через проксі =================
    @Test
    void roomCreate_messyName_isSavedNormalized() {
        // Arrange
        RoomDto messy = new RoomDto(null, "  Gamma    Hall ", 10, null, true, null);

        // Act
        RoomDto created = roomService.create(messy);

        // Assert: аспект підмінив аргумент до того, як RoomService його побачив
        ArgumentCaptor<Room> saved = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Gamma Hall");
        assertThat(created.name()).isEqualTo("Gamma Hall");
    }
}
