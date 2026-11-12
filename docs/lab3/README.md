# Лабораторна робота №3 — Анотації

Три власні анотації, кожна з власним механізмом обробки:

| Тип | Анотація | Механізм | Хто обробляє |
|---|---|---|---|
| 1. Bean Validation constraint | `@ValidBookingSlot` | reflection під час виконання | Hibernate Validator через `@Constraint(validatedBy = BookingSlotValidator.class)` |
| 2. Композитна (meta-) анотація | `@PrivilegedWrite` | AOP / проксі | Spring Security (`@PreAuthorize`) і Spring TX (`@Transactional`) знаходять мета-анотації |
| 3. Резолвер аргументів MVC | `@CurrentUserId` | `HandlerMethodArgumentResolver` | Spring MVC перед викликом методу контролера |

Усі три мають `@Retention(RUNTIME)`: обробники бачать їх через reflection під час роботи програми.

```
mvn clean verify
Tests run: 127, Failures: 0, Errors: 0, Skipped: 0
All coverage checks have been met.   (інструкції 68.7%, гілки 74.4%)
```

---

## Тип 1 — `@ValidBookingSlot`

**Файли:** [`ValidBookingSlot.java`](../../src/main/java/com/example/booking/booking/validation/ValidBookingSlot.java),
[`BookingSlotValidator.java`](../../src/main/java/com/example/booking/booking/validation/BookingSlotValidator.java),
застосовано до [`BookingDto`](../../src/main/java/com/example/booking/booking/BookingDto.java).

**Бізнес-правило** (стосується двох полів одночасно, тому анотація рівня класу, `@Target(TYPE)`):
1. `endTime` пізніше за `startTime`;
2. бронювання починається і закінчується в один календарний день;
3. тривалість від `minMinutes()` до `maxHours()` (за замовчуванням 15 хв … 8 год). Межі — елементи анотації, їх можна змінити: `@ValidBookingSlot(maxHours = 4)`.

Для `null`-полів constraint повертає `true`: їх перевіряє `@NotNull`, щоб не дублювати повідомлення.
Порушення прив'язується до поля `endTime`, тому API відповідає `400` з `detail: "endTime: <пояснення>"`, а UI показує те саме пояснення у flash-повідомленні.

| AC | Як виконано | Тест |
|---|---|---|
| AC1 — валідатор знаходиться автоматично | `@Constraint(validatedBy = BookingSlotValidator.class)`; у коді застосунку немає `new BookingSlotValidator()`. Тест бере звичайний `Validation.buildDefaultValidatorFactory()` і перевіряє, що порушення дав саме `BookingSlotValidator` | `BookingSlotValidatorTest.validate_dtoWithClassLevelAnnotation_bookingSlotValidatorIsDiscoveredAutomatically`; через реальний запит — `BookingControllerMvcTest.create_slotLongerThan8Hours_returns400...` |
| AC2 — коректні дані без порушень | 1 год, рівно 15 хв, рівно 8 год, 18:00–23:59 | `validate_slotWithinRules_noViolations` (×4), `validate_missingTimes_constraintSkipsAndLeavesItToNotNull`, `BookingControllerMvcTest.create_validSlot_reachesServiceAndReturns201` |
| AC3 — порушення з чітким повідомленням | Окреме повідомлення для кожного правила з фактичними значеннями, напр. `Booking must not last longer than 8 hours, but lasts 481 minutes` | `validate_endBeforeStart_...`, `validate_endEqualsStart_...`, `validate_slotOverMidnight_...`, `validate_slotShorterThan15Minutes_...`, `validate_slotLongerThan8Hours_...` |

---

## Тип 2 — `@PrivilegedWrite`

**Файл:** [`PrivilegedWrite.java`](../../src/main/java/com/example/booking/security/PrivilegedWrite.java).

Ця пара анотацій стояла разом на трьох методах — `RoomService.create`, `RoomService.update`, `BookingService.confirm`:

```java
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@Transactional
```

Тепер там одна анотація `@PrivilegedWrite`, яка сама позначена обома. Це той самий патерн, що `@RestController` = `@Controller` + `@ResponseBody`.
Spring шукає анотації з урахуванням мета-анотацій (`AnnotatedElementUtils.findMergedAnnotation`), тому поведінка не змінилася:
- без ролі ADMIN/MANAGER — `AccessDeniedException` до входу в метод і до відкриття транзакції;
- з роллю — метод виконується в транзакції для читання і запису (перевизначає `@Transactional(readOnly = true)` класу).

| AC | Як виконано | Тест |
|---|---|---|
| AC4 — нова анотація позначена обома оригінальними | `@PreAuthorize(...)` + `@Transactional` на `@PrivilegedWrite`; Spring знаходить ті самі атрибути, що й на оригінальній парі | `PrivilegedWriteTest.privilegedWrite_metaAnnotations_resolveToSameAttributesAsOriginalPair` |
| AC5 — така сама поведінка, як в оригінальних | Мінімальний Spring-контекст (`@EnableMethodSecurity` + `@EnableTransactionManagement`, менеджер транзакцій — мок). Бін `Probe` має два однакові методи: з оригінальними анотаціями і з композитною; кожен тест виконується для обох | `call_userWithoutPrivilegedRole_isDeniedBeforeTransactionStarts` (×2), `call_manager_runsInsideReadWriteTransaction` (×2), на реальному сервісі — `roomServiceCreate_asUser_deniedByComposedAnnotation`, `roomServiceCreate_asAdmin_savesInReadWriteTransactionDespiteReadOnlyClassDefault` |

Також поведінку на живому застосунку перевіряє Postman: `POST confirm as USER -> 403 (@PreAuthorize)` і `POST confirm as MANAGER -> 200`.

---

## Тип 3 — `@CurrentUserId`

**Файли:** [`CurrentUserId.java`](../../src/main/java/com/example/booking/security/CurrentUserId.java),
[`CurrentUserIdArgumentResolver.java`](../../src/main/java/com/example/booking/security/CurrentUserIdArgumentResolver.java),
реєстрація в [`WebMvcConfig.java`](../../src/main/java/com/example/booking/common/WebMvcConfig.java).

Резолвер бере `Authentication` з контексту запиту (`request.getUserPrincipal()`; Spring Security кладе його туди для JWT і для OIDC) і повертає claim `sub`.
Новий ендпоінт використовує його:

```java
// GET /api/bookings/mine -> лише МОЇ бронювання (навіть для ADMIN/MANAGER)
@GetMapping("/mine")
public List<BookingDto> mine(@CurrentUserId String userId) {
    return service.listByOwner(userId);
}
```

| AC | Як виконано | Тест |
|---|---|---|
| AC6 — `supportsParameter` | `true` для `String`-параметра з анотацією, `false` без анотації і для анотованого параметра іншого типу | `CurrentUserIdArgumentResolverTest.supportsParameter_stringParameterWithAnnotation_returnsTrue`, `..._parameterWithoutAnnotation_returnsFalse`, `..._annotatedParameterOfWrongType_returnsFalse` |
| AC7 — `resolveArgument` повертає правильне значення | `sub` з JWT (API) і з OIDC ID-токена (UI); без автентифікації або з невідомим principal — зрозумілий `IllegalStateException` | `resolveArgument_jwtAuthenticatedRequest_returnsSubjectClaim`, `..._oidcAuthenticatedRequest_returnsSubjectFromIdToken`, `..._anonymousRequest_throwsIllegalState`, `..._unsupportedPrincipalType_throwsIllegalState` |
| AC8 — значення реально використовується в методі | Справжній HTTP-запит через зріз Spring MVC (`@WebMvcTest`, як `ProductPublicEndpointsWebMvcTest` з лекції). Резолвер реєструє сам застосунок через `WebMvcConfig`, у тесті він вручну не підключається; резолвлений `sub` передається в `service.listByOwner(...)`, відповідь містить бронювання саме цього користувача | `BookingControllerMvcTest.mine_authenticatedRequest_passesResolvedUserIdToServiceAndReturnsItsBookings`, `mine_differentUser_serviceReceivesThatUsersId` |

На живому застосунку: Postman `GET my bookings as USER (@CurrentUserId, lab 3) -> 200` перевіряє, що всі `ownerId` дорівнюють `sub` з токена,
а `GET my bookings as MANAGER ... -> 200 []` — що менеджер бачить тут лише свої бронювання (жодного), хоча `GET /api/bookings` показує йому всі.

---

## Як перевірити

```
mvn clean verify
```

Окремо тести лаби 3: `BookingSlotValidatorTest`, `PrivilegedWriteTest`, `CurrentUserIdArgumentResolverTest`, `BookingControllerMvcTest`.

Два тести піднімають Spring, бо саме Spring обробляє ці анотації:
* `PrivilegedWriteTest` — мінімальний контекст (method security + транзакції), без нього AC5 перевірити неможливо;
* `BookingControllerMvcTest` — `@WebMvcTest`, лише веб-шар, щоб перевірити реєстрацію резолвера в `WebMvcConfig`.

Обидва без БД, вебсервера і Keycloak (OAuth2-автоконфігурації вимкнено, як у лекційному коді).
