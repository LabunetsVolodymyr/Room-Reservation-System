# Лабораторна робота №2 — Unit-тестування

Звіт про виконання: як кожен критерій прийнятності закрито в коді, яке покриття і які проблеми знайдено.

## Результат

```
mvn clean verify
Tests run: 96, Failures: 0, Errors: 0, Skipped: 0
All coverage checks have been met.
BUILD SUCCESS
```

| Метрика JaCoCo | Покрито | Відсоток | Поріг у `pom.xml` |
|---|---|---|---|
| Інструкції | 862 з 1304 | **66.1%** | 60% |
| Гілки | 47 з 64 | **73.4%** | 60% |

Класи бізнес-логіки, визначені для лабораторної: `BookingService` (100% інструкцій, 14/14 гілок) і `RoomService` (100% інструкцій).

Звіт: `target/site/jacoco/index.html` (з'являється після `mvn test` / `mvn verify`).
Не покриті модульними тестами: `SecurityConfig` (конфігурація Spring, перевіряється інтеграційно/Postman), `WebController` (Thymeleaf UI, перевіряється вручну через браузер) і `BookingApplication.main`.

## Тестові класи

| Клас SUT | Тестовий клас | Тестів |
|---|---|---|
| `BookingService` | `booking/BookingServiceTest` | 36 |
| `RoomService` | `room/RoomServiceTest` | 12 |
| `BookingController` | `booking/BookingControllerTest` | 8 |
| `RoomController` | `room/RoomControllerTest` | 6 |
| `BookingMapper` / `RoomMapper` | `BookingMapperTest` / `RoomMapperTest` | 5 + 5 |
| `GlobalExceptionHandler` | `common/GlobalExceptionHandlerTest` | 6 |
| `CurrentUser` | `security/CurrentUserTest` | 9 |
| `JwtRoleConverter` | `security/JwtRoleConverterTest` | 9 |

## Відповідність критеріям прийнятності

### AC1 — звичайні вхідні дані, реальне твердження
Кожен тест має `assertThat(...)` на результат або стан, а не лише «не впав». Приклади:
`BookingServiceTest.create_validData_savesPendingBookingForCurrentUser`, `RoomServiceTest.get_existingRoom_returnsDto`,
`BookingServiceTest.confirm_pendingBooking_setsStatusConfirmed`.

### AC2 — невалідні дані, граничні значення, відмови
Окремі тести на кожну гілку обробки помилок, з перевіркою типу винятку **і** повідомлення:
* `create_endBeforeStart_...` і межовий `create_endEqualsStart_throwsIllegalArgument` (endTime == startTime);
* `create_roomNotFound_...`, `create_inactiveRoom_...`;
* `update_confirmedBooking_throwsIllegalStateAndSavesNothing`;
* `confirm_nonPendingBooking_...` — `@ParameterizedTest` + `@EnumSource` (CONFIRMED, CANCELLED);
* `get_foreignBookingAsRegularUser_throwsAccessDenied`, `RoomServiceTest.create_duplicateName_propagatesDataIntegrityViolation`;
* `JwtRoleConverterTest.extractRoles_malformedAccessToken_returnsEmpty` (резервний варіант — порожній список).

### AC3 — залежності імітуються, без Spring
Усі тести використовують `@ExtendWith(MockitoExtension.class)` + `@Mock` / `@InjectMocks`.
Немає `@SpringBootTest`, `@WebMvcTest`, `@MockBean`. MapStruct-мапери створюються через `Mappers.getMapper(...)`
(у `BookingServiceTest` / `RoomServiceTest` — як `@Spy`, тобто справжній мапер, впроваджений у SUT).

### AC4 — тест падає, якщо зламати логіку
Виконано три навмисні мутації `BookingService`. Для кожної збережено вивід Maven до і після відновлення:

| № | Що зламано | Які тести впали | Доказ |
|---|---|---|---|
| 1 | `validateTimes`: `isAfter` → `isBefore` | 10 тестів (`create_endBeforeStart_...`, `create_validData_...`, `update_pendingOwnBooking_...` та ін.) | [зламано](mutation-1-validateTimes-broken.txt) / [відновлено](mutation-1-validateTimes-restored.txt) |
| 2 | `create`: прибрано `booking.setOwnerId(currentUser.id())` | `create_validData_savesPendingBookingForCurrentUser`, `create_clientSuppliesOwnerAndStatus_valuesAreIgnored` | [зламано](mutation-2-ownerId-broken.txt) / [відновлено](mutation-2-ownerId-restored.txt) |
| 3 | `update`: умову `status != PENDING` замінено на `false` | `update_confirmedBooking_throwsIllegalStateAndSavesNothing` | [зламано](mutation-3-updateStatus-broken.txt) / [відновлено](mutation-3-updateStatus-restored.txt) |

Після кожної мутації код повернуто до початкового стану, і всі тести знову проходять.
Скріншоти з IntelliJ (червоний / зелений запуск) додаються в цю папку як `mutation-N-broken.png` / `mutation-N-restored.png`.

### AC5 — ізольованість і детермінованість
* Репозиторії замокані — реальної БД, мережі й файлової системи немає (JWT у `JwtRoleConverterTest` будується в пам'яті).
* Час — тільки константи `LocalDateTime.of(2026, 12, 1, 10, 0)` / `Instant.parse(...)`; `now()`, `Random`, `Thread.sleep` у тестах не використовуються.
* Кожен тест готує власні дані; `SecurityContextHolder` очищується в `@AfterEach` (`CurrentUserTest`), тож порядок запуску не важливий.

### AC6 — назви та Arrange-Act-Assert
* Назви у форматі `метод_сценарій_результат`: `update_inactiveRoom_throwsIllegalArgumentAndSavesNothing`.
* Тіло кожного тесту розділене коментарями `// Arrange`, `// Act`, `// Assert`.
  Для винятків дія і перевірка розділені через `Throwable thrown = catchThrowable(...)` → `assertThat(thrown)...`.

### AC7 — перевірка побічних ефектів
* `ArgumentCaptor<Booking>` + `verify(repository).save(captor.capture())` — перевіряє, **що саме** збережено
  (власник, статус PENDING, кімната, час): `create_validData_...`, `update_pendingOwnBooking_...`, `update_differentRoom_...`.
* `verify(repository, never()).save(...)` / `delete(...)` — у сценаріях відмови нічого не записується.
* `InOrder`: `RoomServiceTest.delete_existingRoom_deletesAndFlushesInOrder` (спочатку `delete`, потім `flush`).
* Зміна статусу перевіряється на самій сутності: `confirm_pendingBooking_setsStatusConfirmed`, `cancel_ownBooking_setsStatusCancelled`.
* `verify(repository).findByOwnerIdOrderByStartTimeDesc(USER_ID)` + `never()` для іншого запиту в `list_*`.

### AC8 — AC1 і AC2 для кожного публічного методу
| Метод | AC1 (звичайний сценарій) | AC2 (відмова / межа) |
|---|---|---|
| `BookingService.list` | `list_regularUser_returnsOnlyOwnBookings`, `list_privilegedUser_returnsAllBookings` | `list_userWithoutBookings_returnsEmptyList` |
| `BookingService.get` | `get_ownBooking_...`, `get_foreignBookingAsPrivilegedUser_...` | `get_bookingNotFound_...`, `get_foreignBookingAsRegularUser_...` |
| `BookingService.create` | `create_validData_...` | `create_endBeforeStart_...`, `create_endEqualsStart_...`, `create_roomNotFound_...`, `create_inactiveRoom_...` |
| `BookingService.update` | `update_pendingOwnBooking_...`, `update_differentRoom_...` | `update_confirmedBooking_...`, `update_endBeforeStart_...`, `update_roomNotFound_...`, `update_inactiveRoom_...`, `update_foreign..._throwsAccessDenied`, `update_bookingNotFound_...` |
| `BookingService.delete` | `delete_ownBooking_deletesIt` | `delete_foreign..._throwsAccessDenied...`, `delete_bookingNotFound_...` |
| `BookingService.confirm` | `confirm_pendingBooking_setsStatusConfirmed` | `confirm_nonPendingBooking_...` (×2), `confirm_foreign..._throwsAccessDenied...`, `confirm_bookingNotFound_...` |
| `BookingService.cancel` | `cancel_ownBooking_...`, `cancel_foreignBookingAsPrivilegedUser_...` | `cancel_foreign..._throwsAccessDenied...`, `cancel_bookingNotFound_...` |
| `RoomService.list` | `list_existingRooms_returnsMappedDtos` | `list_noRooms_returnsEmptyList` |
| `RoomService.get` | `get_existingRoom_returnsDto` | `get_roomNotFound_throwsNotFoundException` |
| `RoomService.create` | `create_validDto_...`, `create_activeNotProvided_...` | `create_duplicateName_propagatesDataIntegrityViolation` |
| `RoomService.update` | `update_existingRoom_...` | `update_roomNotFound_...` |
| `RoomService.delete` | `delete_existingRoom_...` | `delete_roomNotFound_...`, `delete_roomHasBookings_propagatesDataIntegrityViolation` |

`@PreAuthorize` у модульних тестах не діє (проксі створює лише Spring), тому ролеві обмеження перевіряються Postman-колекцією з лабораторної №1.

## Знайдені проблеми

Тести пишуться під **поточну** реалізацію (AC1), тому місця нижче зафіксовані тестами як є і винесені сюди для рішення перед наступним етапом.

| № | Проблема | Де | Тест, що її фіксує |
|---|---|---|---|
| 1 | `cancel()` не перевіряє статус: можна «скасувати» вже скасоване або підтверджене менеджером бронювання (зокрема звичайним `USER`) | `BookingService.cancel` | `cancel_nonPendingOwnBooking_currentlySetsStatusCancelled` (×2) |
| 2 | `delete()` дозволяє власнику видалити вже підтверджене бронювання | `BookingService.delete` | `delete_confirmedOwnBooking_currentlyDeletesIt` |
| 3 | Немає перевірки перетину часу: дві людини можуть забронювати одну кімнату на той самий слот | `BookingService.create` / `update` | — (у репозиторії немає запиту на перетин) |
| 4 | Деактивація кімнати (`active = false`) не зачіпає вже створені бронювання цієї кімнати | `RoomService.update` | — |

Якщо поведінку №1–2 буде виправлено, відповідні тести змінюються на очікування `IllegalStateException`
(спочатку тест падає, потім виправлення — тест проходить).
