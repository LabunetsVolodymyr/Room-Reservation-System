package com.example.booking.aop;

import com.example.booking.room.RoomDto;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Лаба 4: Unit-тест @Around-advice InputNormalizationAspect. ProceedingJoinPoint - мок, без Spring-контексту
 * і без @SpringBootTest. Перевіряємо рішення advice: чи викликається proceed() і з ЯКИМИ аргументами.
 */
@ExtendWith(MockitoExtension.class)
class InputNormalizationAspectTest {

    private final InputNormalizationAspect aspect = new InputNormalizationAspect();

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Test
    void normalizeArguments_recordWithMessyText_proceedsWithCleanedCopy() throws Throwable {
        // Arrange
        RoomDto messy = new RoomDto(null, "  Room   Alpha ", 6, " Big \t room ", true, null);
        when(joinPoint.getArgs()).thenReturn(new Object[]{messy});
        when(joinPoint.proceed(any(Object[].class))).thenReturn("saved");

        // Act
        Object result = aspect.normalizeArguments(joinPoint);

        // Assert: метод отримав очищений DTO, решта полів не змінилась, результат методу повернуто як є
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(joinPoint).proceed(args.capture());
        assertThat(args.getValue()).containsExactly(new RoomDto(null, "Room Alpha", 6, "Big room", true, null));
        assertThat(result).isEqualTo("saved");
        verify(joinPoint, never()).proceed();
    }

    @Test
    void normalizeArguments_alreadyCleanArguments_proceedsWithOriginalArguments() throws Throwable {
        // Arrange
        RoomDto clean = new RoomDto(null, "Room Alpha", 6, null, true, null);
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L, clean});
        when(joinPoint.proceed()).thenReturn("saved");

        // Act
        Object result = aspect.normalizeArguments(joinPoint);

        // Assert: нічого міняти - звичайний proceed(), аргументи не підмінялись
        assertThat(result).isEqualTo("saved");
        verify(joinPoint).proceed();
        verify(joinPoint, never()).proceed(any(Object[].class));
    }

    @Test
    void normalizeArguments_plainStringAndOtherTypes_onlyStringIsNormalized() throws Throwable {
        // Arrange: Long і null не чіпаються, String - нормалізується
        when(joinPoint.getArgs()).thenReturn(new Object[]{42L, "  hello   world ", null});

        // Act
        aspect.normalizeArguments(joinPoint);

        // Assert
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(joinPoint).proceed(args.capture());
        assertThat(args.getValue()).containsExactly(42L, "hello world", null);
    }

    @Test
    void normalizeArguments_targetMethodThrows_exceptionPropagatesUnchanged() throws Throwable {
        // Arrange: аспект не повинен "ковтати" помилки бізнес-логіки (напр. дубль назви -> 409)
        when(joinPoint.getArgs()).thenReturn(new Object[]{"  Alpha "});
        IllegalStateException failure = new IllegalStateException("duplicate");
        when(joinPoint.proceed(any(Object[].class))).thenThrow(failure);

        // Act
        Throwable thrown = catchThrowable(() -> aspect.normalizeArguments(joinPoint));

        // Assert
        assertThat(thrown).isSameAs(failure);
    }

    @ParameterizedTest(name = "[{0}] -> [{1}]")
    @CsvSource(delimiter = '|', value = {
            "'  Alpha  '        | Alpha",
            "'Room     Alpha'   | Room Alpha",
            "'Alpha'            | Alpha",
            "'   '              | ''"
    })
    void normalizeText_variousInputs_trimsAndCollapsesWhitespace(String input, String expected) {
        // Act
        String result = InputNormalizationAspect.normalizeText(input);

        // Assert
        assertThat(result).isEqualTo(expected);
    }
}
