# Система бронювання кімнат

Додаток на базі Spring Boot для керування переговорними кімнатами та їх бронюваннями. Проєкт є технічним фундаментом семестрової роботи: подвійна модель автентифікації (OIDC + JWT), рольовий доступ, міграції бази даних і простий веб-інтерфейс.

## Особливості

* **Керування кімнатами:** створення, перегляд, оновлення та видалення кімнат (назва, місткість, опис, активність).
* **Система бронювань:** створення бронювань на часовий слот, редагування, скасування та підтвердження. Статуси: `PENDING`, `CONFIRMED`, `CANCELLED`.
* **Подвійна автентифікація (Spring Security + Keycloak):**
    * **OIDC** (Authorization Code Flow, сесія) для користувачів веб-інтерфейсу;
    * **JWT** (`Authorization: Bearer <token>`, stateless) для прямих запитів до REST API.
* **Рольовий доступ:** ролі `ADMIN`, `MANAGER`, `USER` (беруться з `realm_access.roles` токена Keycloak).
* **Два рівні обмеження доступу:** правила в `SecurityFilterChain` (відповідь `403 Forbidden`) та анотації `@PreAuthorize` на методах сервісів (`AccessDeniedException`).
* **Міграції бази даних:** схема з контролем версій через Liquibase (`ddl-auto: validate`).
* **Мапінг Entity ↔ DTO:** MapStruct.
* **Власні анотації (лабораторна №3):** `@ValidBookingSlot` (Bean Validation), `@PrivilegedWrite` (композитна), `@CurrentUserId` (резолвер аргументів MVC). Опис: [docs/lab3/README.md](docs/lab3/README.md).
* **Веб-інтерфейс:** серверні HTML-шаблони Thymeleaf (CRUD для кімнат і бронювань).
* **Контейнеризація:** Docker Compose з PostgreSQL і Keycloak (автоматичний імпорт realm з ролями та тестовими користувачами).
* **Готова колекція запитів:** Postman-колекція з отриманням токенів і сценаріями 200 / 201 / 204 / 401 / 403 / 409.

## Технологічний стек

* **Мова:** Java 21
* **Фреймворк:** Spring Boot 3.5.x (Web, Data JPA, Validation, Security, OAuth2 Client, OAuth2 Resource Server)
* **Інструмент збірки:** Maven
* **База даних:** PostgreSQL 16+ (схема створюється Liquibase)
* **Автентифікація:** Keycloak 26 (OpenID Connect / OAuth 2.0)
* **UI:** Thymeleaf + thymeleaf-extras-springsecurity6
* **Інше:** MapStruct, Lombok
* **Розгортання інфраструктури:** Docker та Docker Compose

## Передумови

* Java 21
* Maven (або запуск через IntelliJ IDEA, яка має вбудований Maven). Maven Wrapper (`mvnw`) у проєкті відсутній
* Docker та Docker Compose
* Postman (для API-колекції)

## Початок роботи

### 1. Запуск інфраструктури (PostgreSQL + Keycloak)

```
docker compose up -d
```

Команда піднімає:

* PostgreSQL на порту `5432` (база `booking`, користувач `booking`, пароль `booking`);
* Keycloak на порту `8180` з автоматично імпортованим realm `booking`.

Зачекайте близько хвилини, поки Keycloak запуститься. Перевірка: http://localhost:8180/realms/booking/.well-known/openid-configuration має повернути JSON.

> **Якщо використовуєте власну базу PostgreSQL**, запускайте лише Keycloak (`docker compose up -d keycloak`), створіть порожню базу `booking` і задайте змінні оточення `DB_USERNAME` та `DB_PASSWORD` (в IntelliJ IDEA: *Run → Edit Configurations → Environment variables*, наприклад `DB_USERNAME=postgres;DB_PASSWORD=...`). Без них використовуються значення з `docker-compose.yml` (`booking` / `booking`). Справжній пароль у `application.yml` не записуйте і не комітьте.

### 2. Запуск застосунку (розробка)

```
# Збірка
mvn clean install

# Запуск
mvn spring-boot:run
```

Або запустіть клас `BookingApplication` з IntelliJ IDEA. Keycloak має бути запущений до старту застосунку.

Під час першого запуску Liquibase автоматично створить таблиці `rooms` і `bookings` та додасть дві початкові кімнати (Alpha, Beta).

Веб-інтерфейс: http://localhost:8080

### 3. Тестові користувачі

| Логін | Пароль | Ролі |
|---|---|---|
| `admin` | `admin` | `ADMIN`, `USER` |
| `manager` | `manager` | `MANAGER`, `USER` |
| `user` | `user` | `USER` |

Консоль адміністратора Keycloak: http://localhost:8180 (`admin` / `admin`).

## Автентифікація та авторизація

Застосунок має два незалежні `SecurityFilterChain`:

| Ланцюжок | Шляхи | Механізм | Сесія |
|---|---|---|---|
| `@Order(1)` | `/api/**` | `oauth2ResourceServer` (JWT) | stateless, CSRF вимкнено |
| `@Order(2)` | усе інше (UI) | `oauth2Login` (OIDC, редирект на Keycloak) | сесія, CSRF увімкнено |

Ролі з токена перетворюються на `GrantedAuthority` з префіксом `ROLE_` (`JwtRoleConverter`). Ідентифікатором власника бронювання є claim `sub` з токена, окрема таблиця користувачів не зберігається.

### Матриця доступу

| Ресурс | Операція | Хто має доступ | Де перевіряється |
|---|---|---|---|
| `/api/rooms` | `GET` | будь-який автентифікований | `SecurityFilterChain` |
| `/api/rooms` | `POST`, `PUT` | `ADMIN`, `MANAGER` | `SecurityFilterChain` + `@PreAuthorize` |
| `/api/rooms/{id}` | `DELETE` | `ADMIN` | `SecurityFilterChain` + `@PreAuthorize` |
| `/api/bookings` | `GET` | `USER` бачить свої, `ADMIN`/`MANAGER` усі | сервіс |
| `/api/bookings/mine` | `GET` | будь-який автентифікований, лише свої | `@CurrentUserId` |
| `/api/bookings` | `POST` | будь-який автентифікований | `SecurityFilterChain` |
| `/api/bookings/{id}` | `PUT`, `DELETE`, `POST .../cancel` | власник або `ADMIN`/`MANAGER` | сервіс (`AccessDeniedException`) |
| `/api/bookings/{id}/confirm` | `POST` | `ADMIN`, `MANAGER` | **лише `@PreAuthorize`** |
| `/admin/**` | UI | `ADMIN` | `SecurityFilterChain` |

Ендпоінт підтвердження бронювання навмисно захищений тільки через `@PreAuthorize` (у коді — через композитну `@PrivilegedWrite`): фільтр пропускає будь-якого автентифікованого користувача, а метод сервісу блокує виклик без потрібної ролі.

## REST API

| Метод | Шлях | Опис |
|---|---|---|
| `GET` | `/api/rooms` | список кімнат |
| `GET` | `/api/rooms/{id}` | кімната за id |
| `POST` | `/api/rooms` | створити кімнату |
| `PUT` | `/api/rooms/{id}` | оновити кімнату |
| `DELETE` | `/api/rooms/{id}` | видалити кімнату |
| `GET` | `/api/bookings` | список бронювань |
| `GET` | `/api/bookings/mine` | лише бронювання поточного користувача (`@CurrentUserId`) |
| `GET` | `/api/bookings/{id}` | бронювання за id |
| `POST` | `/api/bookings` | створити бронювання (`PENDING`) |
| `PUT` | `/api/bookings/{id}` | змінити бронювання (лише `PENDING`) |
| `DELETE` | `/api/bookings/{id}` | видалити бронювання |
| `POST` | `/api/bookings/{id}/confirm` | підтвердити бронювання |
| `POST` | `/api/bookings/{id}/cancel` | скасувати бронювання |

Помилки повертаються у форматі `ProblemDetail`: `400` (валідація), `404` (не знайдено), `409` (конфлікт стану або дублікат), `401` (немає токена), `403` (немає прав).

## Структура проєкту

* `src/main/java/com/example/booking/`
    * `room/`: сутність, DTO, мапер, репозиторій, сервіс і контролер кімнат.
    * `booking/`: сутність, статуси, DTO, мапер, репозиторій, сервіс і контролер бронювань; `booking/validation/` — `@ValidBookingSlot` і його валідатор.
    * `security/`: `SecurityConfig` (два ланцюжки фільтрів), `JwtRoleConverter`, `CurrentUser`, анотації `@PrivilegedWrite`, `@CurrentUserId` і `CurrentUserIdArgumentResolver`.
    * `web/`: контролер Thymeleaf-інтерфейсу.
    * `common/`: глобальна обробка винятків для REST API.
* `src/main/resources/`
    * `db/changelog/`: міграції Liquibase.
    * `templates/`: HTML-шаблони веб-інтерфейсу.
    * `application.yml`: конфігурація застосунку.
* `keycloak/booking-realm.json`: realm Keycloak (ролі, клієнт, тестові користувачі).
* `postman/`: колекція API-запитів.
* `docker-compose.yml`: PostgreSQL і Keycloak.

## Тестування API

У каталозі `postman/` лежить колекція `booking.postman_collection.json`.

1. Імпортуйте її в Postman.
2. Спочатку виконайте папку **0. Tokens (Keycloak)**: вона отримує токени для `admin`, `manager` і `user` та зберігає їх у змінні колекції.
3. Далі запускайте папки **1. Rooms**, **2. Bookings**, **3. Cleanup**. В назві кожного запиту вказано очікуваний статус, а вбудовані тести підсвічують результат.

Токен доступу живе близько 5 хвилин. Якщо запити почали повертати `401`, повторно виконайте папку **0. Tokens**.

## Виконання тестів

Модульні тести (лабораторна №2) написані на JUnit 5 + Mockito + AssertJ і лежать у `src/test/java` у тих самих пакетах, що й код, який вони перевіряють.

```
# лише тести + HTML-звіт покриття
mvn test

# тести + перевірка порогу покриття (мінімум 60% інструкцій і 60% гілок)
mvn verify
```

Звіт JaCoCo: `target/site/jacoco/index.html`. Якщо покриття нижче порогу, `mvn verify` завершується помилкою.

Принципи, яких дотримуються тести:

* **Без Spring-контексту, БД і мережі.** Залежності замокано через `@Mock` / `@InjectMocks` (`MockitoExtension`), MapStruct-мапери створюються через `Mappers.getMapper(...)`. Винятки — `PrivilegedWriteTest` і `BookingControllerMvcTest` (`@WebMvcTest`) з лабораторної №3: ці анотації обробляє лише Spring, тому вони піднімають мінімальний контекст без БД і мережі.
* **Arrange-Act-Assert.** Кожен тест розділено коментарями `// Arrange`, `// Act`, `// Assert`. Для винятків використовується `catchThrowable(...)`, щоб дія і перевірка були окремими кроками.
* **Назви у форматі `метод_сценарій_очікуванийРезультат`**, наприклад `create_endBeforeStart_throwsIllegalArgumentAndSavesNothing`.
* **Перевірка побічних ефектів.** Виклики `repository.save(...)` / `delete(...)` перевіряються через `verify(...)`, `ArgumentCaptor` та `never()`.
* **Детермінованість.** Час задається константами (`LocalDateTime.of(...)`), без `Thread.sleep` і без залежності від порядку тестів. `SecurityContextHolder` очищується після кожного тесту.

### Доказ, що тести перевіряють логіку (AC4)

Щоб показати, що тест справді ловить помилку, тимчасово ламаємо логіку, запускаємо тести, робимо скріншот і повертаємо код назад:

| Зміна в коді | Тест, який має впасти |
|---|---|
| `BookingService.validateTimes`: `isAfter` → `isBefore` | 10 тестів, зокрема `create_endBeforeStart_throwsIllegalArgumentAndSavesNothing`, `create_validData_savesPendingBookingForCurrentUser` |
| `BookingService.create`: прибрати `booking.setOwnerId(currentUser.id())` | `create_validData_savesPendingBookingForCurrentUser`, `create_clientSuppliesOwnerAndStatus_valuesAreIgnored` |
| `BookingService.update`: прибрати перевірку статусу `PENDING` | `update_confirmedBooking_throwsIllegalStateAndSavesNothing` |

Вивід Maven для кожної мутації (до і після відновлення), повна відповідність критеріям лабораторної №2 і список знайдених проблем — у [docs/lab2/README.md](docs/lab2/README.md).