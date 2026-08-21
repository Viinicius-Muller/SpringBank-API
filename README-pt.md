# SpringBank

Leia em [English](README.md)

Uma API REST bancária feita com Spring Boot. O usuário se registra, abre uma ou mais contas e
movimenta dinheiro entre elas com depósitos, saques e transferências. Toda alteração de saldo é uma
transação de banco protegida por PIN e por lock de linha, e cada conta só é acessível pelo seu dono.

## Funcionalidades

- Registro e login com JWT, mais um endpoint de atualização de credenciais
- Um usuário, várias contas — cada uma identificada por um número aleatório de 6 dígitos
- Depósito, saque e transferência, todos protegidos por um PIN por conta
- Extrato paginado por conta: tudo, apenas enviadas ou apenas recebidas
- Respostas de erro em RFC 7807 (`ProblemDetail`) em todos os caminhos
- Schema controlado pelo Flyway e validado contra as entidades na inicialização

## Por Que Foi Feito Assim

**Dinheiro só se move sob lock de linha.** Os três pontos que escrevem saldo — `deposit`, `withdraw`
e `createTransfer` — carregam a conta por uma query `@Lock(PESSIMISTIC_WRITE)` (`SELECT … FOR
UPDATE`), então saques concorrentes são serializados pelo banco em vez de ambos lerem o mesmo saldo
desatualizado. Nada chama `save()`: o dirty checking faz o flush no commit, então o débito, o crédito
e a linha de `transfer` entram juntos ou não entram.

**Transferências travam na ordem do número da conta, então não podem dar deadlock.** O
`createTransfer` sempre trava primeiro o número menor, não importa quem envia, então A→B e B→A ficam
na fila do mesmo lock em vez de cada uma segurar a linha que a outra espera. Dono, PIN, conta ativa e
saldo são checados depois que os dois locks já estão em mãos.

**Um PIN de 6 dígitos é só um milhão de tentativas, por isso ele leva pepper.** O `CustomPinEncoder`
aplica HMAC-SHA256 com um `PIN_PEPPER` guardado no servidor antes do BCrypt em strength 12; as senhas
de usuário usam o bean `@Primary` de BCrypt puro. O pepper vive fora do banco, então uma coluna
`pin_hash` vazada não basta sozinha — o custo é que rotacioná-lo invalida todos os PINs.

**A checagem de dono acontece em exatamente uma função.** O `SecurityFilter` valida o JWT, carrega o
usuário pelo subject (e-mail) do token e rejeita contas desabilitadas — stateless, sem sessão nenhuma.
Toda rota por conta então passa por `AccountUtils.ownedAccount`, que devolve 404 para número
desconhecido e 403 para conta de outro, então os caminhos com e sem lock não têm como divergir.

**As respostas de erro falam o mínimo possível.** Senha errada e e-mail inexistente colapsam em um
único `401 Invalid credentials`, e o `422` de saldo insuficiente não traz valor algum, então a API não
vaza nem existência de usuário nem saldo. Todo o resto é uma exceção tipada mapeada para um status
real e renderizada em RFC 7807.

**O banco dá a palavra final.** `ddl-auto=validate` mantém o schema sob o Flyway; dinheiro é
`DECIMAL(15,2)`/`BigDecimal` comparado com `compareTo`, nunca com `equals`; a exclusão é soft e as
duas foreign keys do extrato são `ON DELETE RESTRICT`. Números de conta são pré-checados antes de um
único insert, porque no Postgres uma violação de unique aborta a transação inteira e um laço de
save-and-retry nunca se recuperaria — a constraint `UNIQUE` é a garantia de verdade, e sua corrida
residual aparece como `409`.

## Stack

- Java 17
- Spring Boot 4.1.0
- Spring Security + JJWT 0.12.6 (HS256)
- Spring Data JPA / Hibernate
- PostgreSQL + Flyway
- Jakarta Bean Validation
- Lombok
- Maven (com wrapper)

## Pré-requisitos

- JDK 17
- Docker (sobe o PostgreSQL via `docker-compose`)

## Começando

1. Crie seu arquivo `.env`:

   ```bash
   cp .env.example .env
   ```

   O `JWT_SECRET` precisa ter no mínimo 256 bits (32+ caracteres) ou o `TokenService.initKey()` falha
   já na inicialização; o `PIN_PEPPER` não pode ser vazio ou o `CustomPinEncoder` se recusa a subir.
   Gere os dois com `openssl rand -base64 48`.

2. Suba o PostgreSQL:

   ```bash
   docker compose up -d
   ```

3. Rode a aplicação:

   ```bash
   ./mvnw spring-boot:run
   ```

   No Windows: `mvnw.cmd spring-boot:run`

   O Flyway aplica de `V1` a `V5` no primeiro boot. A aplicação sobe em `http://localhost:8080`, ou
   no que estiver em `PORT`.

## Uso

Registre-se e guarde o token:

```bash
curl -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"vinicius","email":"vinicius@example.com","password":"supersecret"}'
```

Abra uma conta com um PIN de 6 dígitos — a resposta traz o `accountNumber` gerado:

```bash
curl -X POST http://localhost:8080/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"pin":"123456"}'
```

Deposite e depois transfira para outro número de conta:

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

Leia o extrato (mais recentes primeiro, 10 por página):

```bash
curl -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/transfers/me/482913?page=0&size=10'
```

## Endpoints

| Método | Rota | Acesso | Sucesso |
|---|---|---|---|
| POST | `/auth/register` | público | 201 |
| POST | `/auth/login` | público | 200 |
| PATCH | `/auth/credentials` | autenticado | 200 |
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

O `DELETE /accounts/{accountNumber}` recebe o PIN no corpo e desativa a conta em vez de remover a
linha. As três rotas de extrato aceitam `?page=`, `?size=` (limitado a 100) e `?sort=`, com padrão de
10 linhas ordenadas por `transferDateTime` decrescente.

Todo corpo de requisição é validado antes de chegar em um service: PINs batem com `\d{6}`, valores de
transferência e de caixa são no mínimo `0.01` com precisão de no máximo `DECIMAL(15,2)`, e senhas têm
de 8 a 100 caracteres.

## Estrutura do Projeto

```
src/main/java/vinicius/muller/SpringBank/
├── SpringBankApplication.java
├── controller/        # endpoints REST de Auth, Account e Transfer
├── dto/               # records de request/response, a Bean Validation mora aqui
├── exception/         # exceções próprias + GlobalExceptionHandler (RFC 7807)
├── infra/
│   ├── JpaConfig.java # @EnableJpaAuditing
│   └── security/      # SecurityConfig, SecurityFilter, TokenService, CustomPinEncoder
├── model/             # User, Account, Transfer, AuditBase, Role
├── repository/        # repositórios JPA, incluindo a query com FOR UPDATE
├── service/           # AuthService, AccountService, TransferService
└── utils/             # AccountUtils (posse), AccountNumberGenerator, SecurityUtils

src/main/resources/db/migration/   # V1–V5, append-only
```

O fluxo de camadas é `controller → service → repository`. Os DTOs cruzam a fronteira do controller; as
entidades nunca saem da camada de service.

## Configuração

Definida no `.env` (veja o `.env.example`):

| Variável | Padrão | Observações |
|---|---|---|
| `PORT` | — | obrigatória |
| `DB_URL` | — | `host:porta`, ex. `localhost:5432` |
| `DB_NAME` | — | obrigatória |
| `DB_USERNAME` | — | obrigatória |
| `DB_PASSWORD` | — | obrigatória |
| `JWT_SECRET` | — | obrigatória, no mínimo 256 bits |
| `PIN_PEPPER` | — | obrigatória, não pode ser vazia |
| `JWT_EXPIRATION_MS` | `3600000` | tempo de vida do token |

O resto está em `src/main/resources/application.properties`: `ddl-auto=validate`,
`open-in-view=false` (então associações lazy precisam ser buscadas dentro da transação do service) e
`spring.data.web.pageable.max-page-size=100`, já que o padrão do framework, 2000, é um jeito fácil de
pedir uma página enorme.

## Testes

```bash
./mvnw test      # Windows: mvnw.cmd test
```

14 classes de teste, 126 métodos. Slices `@WebMvcTest` cobrem os controllers, testes unitários com
Mockito puro cobrem os services — incluindo asserções de que os escritores de saldo realmente pegam o
lock e de que o `createTransfer` trava na ordem do número da conta — e há testes separados para o
serviço de JWT, o filtro de segurança, o encoder de PIN, as constraints dos DTOs e o handler de
exceções.

Duas ressalvas que vale deixar claras: `SpringBankApplicationTests.contextLoads` é um `@SpringBootTest`
completo, então precisa de um Postgres de verdade e de um `.env` preenchido; e os slices de controller
desabilitam a filter chain, então provam roteamento e mapeamento de status, não que o endpoint está
protegido. A proteção é coberta pelo `SecurityFilterTest`.

## Status

Projeto pessoal de estudo, não é pronto para produção:

- Sem refresh token e sem lista de revogação — o logout é só do lado do cliente
- O CORS usa os padrões permissivos do Spring, o que serve para desenvolvimento local e nada além
- O `POST /auth/login` não checa a flag `enabled`; só o filtro de JWT rejeita usuários desabilitados,
  então uma conta desabilitada ainda consegue emitir um token que ela não consegue usar
- As senhas de usuário usam BCrypt no strength padrão 10; só os PINs recebem strength 12 e o pepper
- Depósitos e saques não são gravados na tabela `transfer` (ela exige as duas foreign keys), então o
  extrato mostra apenas transferências e o saldo não pode ser reconstruído a partir dele
- `created_by` / `updated_by` continuam nulos — a auditoria está ligada, mas não existe um bean
  `AuditorAware`
- Sem rate limiting e sem CI

Repositório: [Viinicius-Muller/SpringBank-API](https://github.com/Viinicius-Muller/SpringBank-API)
