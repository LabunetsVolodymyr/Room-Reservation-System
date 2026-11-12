package com.example.booking.security;

import com.example.booking.room.Room;
import com.example.booking.room.RoomDto;
import com.example.booking.room.RoomMapper;
import com.example.booking.room.RoomRepository;
import com.example.booking.room.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Лаба 3, тип 2 (AC4-AC5): композитна @PrivilegedWrite поводиться ТАК САМО, як пара
 * @PreAuthorize("hasAnyRole('ADMIN','MANAGER')") + @Transactional.
 *
 * Мета-анотації обробляє лише Spring (проксі), тому тут піднімається МІНІМАЛЬНИЙ Spring-контекст:
 * method security + керування транзакціями + два біни для порівняння. Без БД, вебсервера і Keycloak:
 * менеджер транзакцій і репозиторій - моки.
 */
@SpringJUnitConfig(PrivilegedWriteTest.Config.class)
class PrivilegedWriteTest {

    /** Два однакові методи: один з ОРИГІНАЛЬНИМИ анотаціями, другий - з композитною. */
    static class Probe {
        @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
        @Transactional
        public String original() {
            return "done";
        }

        @PrivilegedWrite
        public String composed() {
            return "done";
        }
    }

    @Configuration
    @EnableMethodSecurity
    @EnableTransactionManagement
    static class Config {
        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        Probe probe() {
            return new Probe();
        }

        @Bean
        RoomRepository roomRepository() {
            return mock(RoomRepository.class);
        }

        @Bean
        RoomService roomService(RoomRepository roomRepository) {
            RoomMapper mapper = Mappers.getMapper(RoomMapper.class);
            return new RoomService(roomRepository, mapper);
        }
    }

    @Autowired
    private Probe probe;
    @Autowired
    private RoomService roomService;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetMocks() {
        reset(roomRepository, transactionManager);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private String call(String method) {
        return method.equals("original") ? probe.original() : probe.composed();
    }

    // ================= AC4: композитна несе ті самі анотації =================
    @Test
    void privilegedWrite_metaAnnotations_resolveToSameAttributesAsOriginalPair() throws Exception {
        // Arrange
        Method original = Probe.class.getMethod("original");
        Method composed = Probe.class.getMethod("composed");

        // Act: так само, як це робить Spring, шукаємо анотації з урахуванням мета-анотацій
        PreAuthorize originalAuth = AnnotatedElementUtils.findMergedAnnotation(original, PreAuthorize.class);
        PreAuthorize composedAuth = AnnotatedElementUtils.findMergedAnnotation(composed, PreAuthorize.class);
        Transactional originalTx = AnnotatedElementUtils.findMergedAnnotation(original, Transactional.class);
        Transactional composedTx = AnnotatedElementUtils.findMergedAnnotation(composed, Transactional.class);

        // Assert
        assertThat(composedAuth).isNotNull();
        assertThat(composedAuth.value()).isEqualTo(originalAuth.value());
        assertThat(composedTx).isNotNull();
        assertThat(composedTx.readOnly()).isEqualTo(originalTx.readOnly()).isFalse();
    }

    // ================= AC5: однакова поведінка під час виконання =================
    @ParameterizedTest
    @ValueSource(strings = {"original", "composed"})
    @WithMockUser(roles = "USER")
    void call_userWithoutPrivilegedRole_isDeniedBeforeTransactionStarts(String method) {
        // Act
        Throwable thrown = catchThrowable(() -> call(method));

        // Assert
        assertThat(thrown).isInstanceOf(AccessDeniedException.class);
        verify(transactionManager, never()).getTransaction(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"original", "composed"})
    @WithMockUser(roles = "MANAGER")
    void call_manager_runsInsideReadWriteTransaction(String method) {
        // Act
        String result = call(method);

        // Assert
        assertThat(result).isEqualTo("done");
        ArgumentCaptor<TransactionDefinition> tx = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(tx.capture());
        assertThat(tx.getValue().isReadOnly()).isFalse();
        verify(transactionManager).commit(any());
    }

    // ================= AC5 на реальному сервісі =================
    @Test
    @WithMockUser(roles = "USER")
    void roomServiceCreate_asUser_deniedByComposedAnnotation() {
        // Arrange
        RoomDto dto = new RoomDto(null, "Gamma", 10, null, true, null);

        // Act
        Throwable thrown = catchThrowable(() -> roomService.create(dto));

        // Assert
        assertThat(thrown).isInstanceOf(AccessDeniedException.class);
        verify(roomRepository, never()).saveAndFlush(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void roomServiceCreate_asAdmin_savesInReadWriteTransactionDespiteReadOnlyClassDefault() {
        // Arrange: клас RoomService має @Transactional(readOnly = true); композитна на методі має це перевизначити
        RoomDto dto = new RoomDto(null, "Gamma", 10, null, true, null);
        when(roomRepository.saveAndFlush(any(Room.class))).thenAnswer(inv -> {
            Room saved = inv.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        // Act
        RoomDto result = roomService.create(dto);

        // Assert
        assertThat(result.id()).isEqualTo(5L);
        ArgumentCaptor<TransactionDefinition> tx = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(tx.capture());
        assertThat(tx.getValue().isReadOnly()).isFalse();
    }
}
