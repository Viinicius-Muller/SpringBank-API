# SpringBank

Read in [Português](README-pt.md)

A REST banking API built with Spring Boot. Users register, open one or more accounts, and move money
between them with deposits, withdrawals and transfers. Every balance change is a PIN-protected,
row-locked database transaction, and every account is reachable only by its owner.

## Features

- JWT registration and login, plus a credential-update endpoint
- One user, many accounts — each keyed by a random 6-digit account number
- Deposit, withdraw and transfer, all guarded by a per-account PIN
- Paginated statement per account: everything, sent only, or received only
- RFC 7807 `ProblemDetail` responses for every error path
- Swagger UI, with the JWT wired into the *Authorize* button
- Flyway-owned schema validated against the entities at startup

## Why It's Built This Way

**Money moves under a row lock.** All three balance writers — `deposit`, `withdraw` and
`createTransfer` — load the account through a `@Lock(PESSIMISTIC_WRITE)` query (`SELECT … FOR
UPDATE`), so concurrent withdrawals serialise at the database instead of both reading the same stale
balance. Nothing calls `save()`: dirty checking flushes at commit, so the debit, the credit and the
`transfer` row land together or not at all.

**Transfers lock in account-number order, so they cannot deadlock.** `createTransfer` always locks
the smaller number first, whichever side is sending, so A→B and B→A queue on the same lock instead of
each holding the row the other waits for. Ownership, PIN, active flags and balance are all checked
after both locks are held.

**A 6-digit PIN is only a million guesses, so it gets a pepper.** `CustomPinEncoder` runs HMAC-SHA256
with a server-side `PIN_PEPPER` before BCrypt at strength 12; user passwords use the plain `@Primary`
BCrypt bean. The pepper lives outside the database, so a leaked `pin_hash` column is not enough on its
own — the trade is that rotating it invalidates every stored PIN.

**Ownership is decided in exactly one function.** `SecurityFilter` validates the JWT, loads the user
by the token's e-mail subject and rejects disabled accounts — stateless, no session anywhere. Every
account-scoped route then goes through `AccountUtils.ownedAccount`, which 404s an unknown number and
403s someone else's account, so the locking and non-locking paths cannot drift apart.

**Errors say as little as they can.** A wrong password and an unknown e-mail collapse into one
`401 Invalid credentials`, and the `422` for insufficient funds carries no amounts, so the API leaks
neither user existence nor balances. Everything else is a typed exception mapped to a real status and
rendered as RFC 7807.

**The database has the last word.** `ddl-auto=validate` keeps the schema Flyway's; money is
`DECIMAL(15,2)`/`BigDecimal` compared with `compareTo`, never `equals`; deletes are soft and both
ledger foreign keys are `ON DELETE RESTRICT`. Account numbers are pre-checked before a single insert,
because on Postgres a unique violation aborts the whole transaction and a save-and-retry loop could
never recover — the `UNIQUE` constraint is the real guarantee, and its residual race surfaces as a
`409`.

## Tech Stack

- Java 17
- Spring Boot 4.1.0
- Spring Security + JJWT 0.12.6 (HS256)
- Spring Data JPA / Hibernate
- PostgreSQL + Flyway
- Jakarta Bean Validation
- springdoc-openapi 3.1.0 (Swagger UI)
- Lombok
- Maven (wrapper included)

## Prerequisites

- JDK 17
- Docker (runs PostgreSQL via `docker-compose`)

## Getting Started

1. Create your `.env` file:

   ```bash
   cp .env.example .env
   ```

   `JWT_SECRET` must be at least 256 bits (32+ characters) or `TokenService.initKey()` fails at
   startup; `PIN_PEPPER` must not be blank or `CustomPinEncoder` refuses to build. Generate both with
   `openssl rand -base64 48`.

2. Start PostgreSQL:

   ```bash
   docker compose up -d
   ```

3. Run the application:

   ```bash
   ./mvnw spring-boot:run
   ```

   On Windows: `mvnw.cmd spring-boot:run`

   Flyway applies `V1`–`V5` on first boot. The app starts on `http://localhost:8080`, or whatever
   `PORT` says.

4. Open the API docs at `http://localhost:8080/swagger-ui.html`.

## API Docs

Swagger UI lives at `/swagger-ui.html` and the raw OpenAPI document at `/v3/api-docs`; both are
public, everything else still needs a token. To call a protected endpoint from the UI, take the
`token` from `POST /auth/register` or `POST /auth/login`, paste it into **Authorize**, and it is sent
as `Authorization: Bearer …` on every request — `register` and `login` are the only operations marked
as needing no token.

## Usage

Register and keep the token:

```bash
curl -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"vinicius","email":"vinicius@example.com","password":"supersecret"}'
```

Open an account with a 6-digit PIN — the response carries the generated `accountNumber`:

```bash
curl -X POST http://localhost:8080/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"pin":"123456"}'
```

Deposit, then transfer to another account number:

```bash
curl -X POST http://localhost:8080/accounts/482913/deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"value":250.00,"pin":"123456"}'

curl -X POST http://localhost:8080/transfers/482913 \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"receiverAccountNumber":"771204","value":80.50,"pin":"123456"}'
```

Read the statement (newest first, 10 per page):

```bash
curl -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/transfers/me/482913?page=0&size=10'
```

## Endpoints

| Method | Path | Auth | Success |
|---|---|---|---|
| POST | `/auth/register` | public | 201 |
| POST | `/auth/login` | public | 200 |
| PATCH | `/auth/credentials` | authenticated | 200 |
| POST | `/accounts` | `ROLE_MEMBER` | 201 |
| GET | `/accounts` | `ROLE_MEMBER` | 200 |
| GET | `/accounts/{accountNumber}` | `ROLE_MEMBER` | 200 |
| POST | `/accounts/{accountNumber}/deposit` | `ROLE_MEMBER` | 200 |
| POST | `/accounts/{accountNumber}/withdraw` | `ROLE_MEMBER` | 200 |
| DELETE | `/accounts/{accountNumber}` | `ROLE_MEMBER` | 204 |
| POST | `/transfers/{accountNumber}` | `ROLE_MEMBER` | 201 |
| GET | `/transfers/me/{accountNumber}` | `ROLE_MEMBER` | 200 |
| GET | `/transfers/me/{accountNumber}/sent` | `ROLE_MEMBER` | 200 |
| GET | `/transfers/me/{accountNumber}/received` | `ROLE_MEMBER` | 200 |

`DELETE /accounts/{accountNumber}` takes the PIN in the body and deactivates the account rather than
removing the row. The three statement routes accept `?page=`, `?size=` (capped at 100) and `?sort=`,
defaulting to 10 rows sorted by `transferDateTime` descending.

Every request body is validated before it reaches a service: PINs match `\d{6}`, transfer and cash
values are at least `0.01` with at most `DECIMAL(15,2)` precision, and passwords are 8–100
characters.

## Project Structure

```
src/main/java/vinicius/muller/SpringBank/
├── SpringBankApplication.java
├── controller/        # Auth, Account and Transfer REST endpoints
├── dto/               # request/response records, Bean Validation lives here
├── exception/         # custom exceptions + GlobalExceptionHandler (RFC 7807)
├── infra/
│   ├── JpaConfig.java # @EnableJpaAuditing
│   └── security/      # SecurityConfig, SecurityFilter, TokenService, CustomPinEncoder
├── model/             # User, Account, Transfer, AuditBase, Role
├── repository/        # JPA repositories, including the FOR UPDATE lock query
├── service/           # AuthService, AccountService, TransferService
└── utils/             # AccountUtils (ownership), AccountNumberGenerator, SecurityUtils

src/main/resources/db/migration/   # V1–V5, append-only
```

Layering is `controller → service → repository`. DTOs cross the controller boundary; entities never
leave the service layer.

## Configuration

Defined in `.env` (see `.env.example`):

| Variable | Default | Notes |
|---|---|---|
| `PORT` | — | required |
| `DB_URL` | — | `host:port`, e.g. `localhost:5432` |
| `DB_NAME` | — | required |
| `DB_USERNAME` | — | required |
| `DB_PASSWORD` | — | required |
| `JWT_SECRET` | — | required, at least 256 bits |
| `PIN_PEPPER` | — | required, non-blank |
| `JWT_EXPIRATION_MS` | `3600000` | token lifetime |

The rest lives in `src/main/resources/application.properties`: `ddl-auto=validate`,
`open-in-view=false` (so lazy associations must be fetched inside the service transaction), and
`spring.data.web.pageable.max-page-size=100`, since the framework default of 2000 is an easy way to
ask for a very large page.

## Tests

```bash
./mvnw test      # Windows: mvnw.cmd test
```

14 test classes, 126 test methods. `@WebMvcTest` slices cover the controllers, plain Mockito unit
tests cover the services — including assertions that the balance writers really take the lock and
that `createTransfer` locks in account-number order — and there are separate tests for the JWT
service, the security filter, the PIN encoder, the DTO constraints and the exception handler.

Two caveats worth stating: `SpringBankApplicationTests.contextLoads` is a full `@SpringBootTest`, so
it needs a live Postgres and a populated `.env`; and the controller slices disable the filter chain,
so they prove routing and status mapping, not that an endpoint is protected. Protection is covered by
`SecurityFilterTest` instead.

## Status

Personal learning project, not production-ready:

- No refresh tokens, no revocation list — logout is client-side only
- CORS uses Spring's permissive defaults, fine for local work and nothing else
- `POST /auth/login` does not check the `enabled` flag; only the JWT filter rejects disabled users, so
  a disabled account can still mint a token it cannot use
- User passwords use BCrypt at the default strength 10; only PINs get strength 12 plus the pepper
- Deposits and withdrawals are not written to the `transfer` table (it requires both foreign keys), so
  the statement shows transfers only and a balance cannot be re-derived from the ledger
- `created_by` / `updated_by` stay null — auditing is enabled, but there is no `AuditorAware` bean
- No rate limiting, no CI
- Swagger UI is exposed unauthenticated, which is fine locally and not beyond that

Repository: [Viinicius-Muller/SpringBank-API](https://github.com/Viinicius-Muller/SpringBank-API)
