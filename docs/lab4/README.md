# Лабораторна робота №4 — AOP

Три пари «власна анотація + аспект», кожна з іншим типом advice і реальною наскрізною функціональністю:

| Анотація | Аспект | Advice | Що робить | Де застосовано |
|---|---|---|---|---|
| `@Audited("...")` | `AuditAspect` | `@AfterReturning` | Журнал аудиту: хто, що, з яким об'єктом і з яким результатом. Пише в лог `AUDIT` і тримає останні 200 подій у `AuditLog` | `BookingService.confirm / cancel / delete`, `RoomService.delete` |
| `@RateLimited(maxCalls, perSeconds)` | `RateLimitAspect` | `@Before` | Обмеження частоти викликів для кожного користувача (ковзне вікно). Понад ліміт — `RateLimitExceededException` → **429** + `Retry-After` | `BookingService.create` (5 на хвилину) |
| `@NormalizedInput` | `InputNormalizationAspect` | `@Around` | Очищує текст в аргументах (`"  Room   Alpha "` → `"Room Alpha"`) і викликає метод через `proceed(newArgs)` | `RoomService.create / update` |

Код: пакет [`aop`](../../src/main/java/com/example/booking/aop). Залежність `spring-boot-starter-aop` додано в `pom.xml`.
Час аспекти беруть із біна `Clock` ([`ClockConfig`](../../src/main/java/com/example/booking/common/ClockConfig.java)), тому тести детерміновані.

```
mvn clean verify
Tests run: 153, Failures: 0, Errors: 0, Skipped: 0
All coverage checks have been met.   (інструкції 74.7%, гілки 81.0%)
```

## Чому саме ці три

- **Аудит** — класична наскрізна задача: однаково потрібна в кількох сервісах, а бізнес-логіку не цікавить. `@AfterReturning`, бо в журнал має потрапляти лише **успішна** дія разом з її результатом (новий статус бронювання).
- **Rate limiting** — захист від спаму бронюваннями. `@Before`: перевірка має відбутися **до** методу; виняток не дає методу виконатися.
- **Нормалізація вводу** — `"Alpha"` і `"Alpha "` не повинні стати двома різними кімнатами. `@Around`, бо лише він може викликати метод з **іншими аргументами** (`proceed(Object[])`). Record-DTO незмінні, тому новий екземпляр будується через reflection (`getRecordComponents()` + канонічний конструктор).

## Відповідність сценаріям

| Сценарій | Як виконано | Тест |
|---|---|---|
| Анотація правильно пов'язана з аспектом | Усі три: `@Retention(RUNTIME)`, `@Target(METHOD)`; pointcut `@annotation(...)` | `AspectProxyTest` (усі тести через проксі) |
| Поведінка аспекту помітна | Запис у журналі / лог `AUDIT`; виняток 429; змінений аргумент | `cancel_calledFromOutside_goesThroughProxyAndIsAudited`, `create_moreThanFiveTimesPerMinute_sixthCallIsBlockedBeforeMethodRuns`, `roomCreate_messyName_isSavedNormalized` |
| Зовнішній виклик активує аспект | Виклик з тесту (іншого класу) через проксі, який інжектить Spring | ті самі тести `AspectProxyTest` + `injectedService_isCglibProxy` |
| **Самовиклик НЕ активує аспект** | [`BookingService.cancelViaSelfInvocation`](../../src/main/java/com/example/booking/booking/BookingService.java) викликає `this.cancel(id)`: бронювання скасовується, але запису аудиту немає і помилки теж немає. Це навмисна демонстрація, не баг | `cancel_calledViaThisInsideService_bypassesProxyAndIsNotAudited` |
| Advice тестується модульно, без Spring | `ProceedingJoinPoint` / `JoinPoint` — моки, аспект через `new`, без `@SpringBootTest`. Перевіряється рішення advice: чи викликано `proceed()`, з якими аргументами, що записано, коли кидається виняток | `InputNormalizationAspectTest` (`@Around`: `proceed(args)` vs `proceed()`, `never()`), `AuditAspectTest`, `RateLimitAspectTest` |

## Самовиклик: два шляхи до одного методу

```java
// 1) Ззовні (контролер, тест): bookingService - це ПРОКСІ -> AuditAspect спрацьовує
bookingService.cancel(5L);                     // журнал: BOOKING_CANCELLED by user-1 on 5

// 2) Зсередини BookingService: this - сам об'єкт, проксі оминається -> аспект мовчить
public BookingDto cancelViaSelfInvocation(Long id) {
    return this.cancel(id);                    // бронювання скасовано, запису в журналі НЕМАЄ
}
```

Spring Boot за замовчуванням створює **CGLIB-проксі** (підклас `BookingService`). Інші біни отримують проксі, а `this` всередині класу — завжди сам об'єкт, тож advice не викликається і ніякого повідомлення про це немає.

## Порядок з іншими проксі

На `BookingService.confirm` разом стоять `@PrivilegedWrite` (лаба 3: `@PreAuthorize` + `@Transactional`) і `@Audited`. Якщо в користувача немає ролі, `@PreAuthorize` кидає `AccessDeniedException`, метод не завершується успішно, і `@AfterReturning` **не** створює запис — у журнал потрапляють лише дії, що справді відбулися (`cancel_targetMethodFails_isNotAudited`).

## Як перевірити на живому застосунку

- Аудит: `POST /api/bookings/{id}/confirm` від MANAGER → у консолі рядок `BOOKING_CONFIRMED by <sub> on <id> [BookingService.confirm(..)] -> status=CONFIRMED`.
- Rate limit: 6 разів поспіль `POST /api/bookings` від одного користувача → шостий отримує `429 Too Many Requests` із заголовком `Retry-After`.
- Нормалізація: `POST /api/rooms` з `"name": "  Gamma   Hall "` → у відповіді `"name": "Gamma Hall"`.
