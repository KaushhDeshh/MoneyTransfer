# MoneyTransfer
A Spring Boot application for safely transferring money between accounts with FX conversion and transaction fees.

## Prerequisites

- Java 17
- Maven 3.8+

## How to Run

1. Clone the repository:
```bash
git clone https://github.com/yourusername/money-transfer-app.git
cd money-transfer-app
```

2. Build the project:
```bash
mvn clean install
```

3. Run the application:
```bash
mvn spring-boot:run
```

Or, run the generated jar:
```bash
java -jar target/money-transfer-app-0.0.1-SNAPSHOT.jar
```

4. Run tests:
```bash
mvn test
```

I have made little rest controllers you can use to test the functionality these are just temporary and have no validations done to them
Currency:
```http
POST http://localhost:8080/test/currency
Content-Type: application/json

{
    "code": "USD",
    "name": "US Dollar"
}
```

Account:
```http
POST http://localhost:8080/test/account
{
  "name": "Bob",
  "currencyCode": "USD",
  "initialBalance": 1000.00
}
```

Transfer:
```http
POST http://localhost:8080/test/transfer
 {
  "senderAccountId": 1,
  "receiverAccountId": 2,
  "amount": 100.00,
  "currency": "USD"
}
```
I have added a command Line runner to prelaod data you can also take a look at this if you want to test it.

## Design
This application is designed as a backend service responsible for handling money transfers between accounts. All business logic related to transfers is encapsulated in the TransferService. It manages validation, currency conversion, fee application, and ensures transactional integrity even under concurrent load.


### Functional

1. Designed TransferPolicy entity to store fee configuration with caching to reduce DB load; structured for future extensibility to support dynamic transfer rules and adaptable policy-based transaction behavior.
2. Used an in-memory HashMap to mock FX rates for simplicity, avoiding DB persistence due to frequent changes. In production, rates would be fetched from an external source or cached in Redis.(I could be wrong here) :)
3. The TransferService is designed to be interfaced via DTOs with internal validation. External callers must handle business exceptions like insufficient funds or invalid currency to ensure correct domain behavior.
4. The Transacions will roll back immediatly in case of any error.
### Data Model
1. Account: Represents a user’s bank account. Each account has a unique ID, name, currency, and balance. The currency determines what denomination the account operates in this is critical to enforcing transfer rules.
2. Currency: A simple entity storing a currency code (e.g., USD, JPY). Accounts are strictly tied to a base currency, and transfers must respect this constraint.
3. Transfer: Represents a money transfer operation. It contains sender, receiver, transfer amount, applied fee, FX rate, and current state (e.g., PROCESSING, COMPLETED, FAILED). This allows us to track and audit transfers robustly.
5. TransferRequestDTO: A data transfer object used to initiate a new transfer. This DTO acts as the validated boundary input and is used to construct the internal Transfer entity within a controlled service-layer transaction.

#### Improvents
Implement proper authentication/authorization to ensure only permitted users can initiate transfers from their accounts. Use UUIDs instead of numeric IDs for accounts and transfers to avoid ID enumeration and enhance security.

### Transaction Accuracy
To ensure precision in all financial operations, the system uses BigDecimal for amounts, FX rates, and fees—avoiding issues from floating-point types. A CommonHelper utility standardizes rounding to 4 decimal places using RoundingMode.HALF_UP:
I did use a scale of 4 for Big Decimal but this in prod would be modified to Bank standards
```Java
 public BigDecimal round(BigDecimal amount) {
        if (amount == null) return null;
        return amount.setScale(MONEY_SCALE, ROUNDING_MODE);
    }
```

### Safe Concurrency
I chose DB-level locking over application locks because multiple services might interact with the database. DB locks are more reliable for ensuring consistency across distributed systems.

To handle concurrent transfers safely, I evaluated three common strategies:

1. Optimistic Locking: Reduces contention but risks stale reads—unsuitable for precise balance handling where overdrafts must be avoided.

2. Eventual Consistency: Double-entry ledgers or outbox patterns offer scalability, but require complex reconciliation and were overkill for this task.

3. Pessimistic Locking (Chosen): Guarantees strong consistency by locking account rows using SELECT FOR UPDATE.

To avoid deadlocks, accounts are locked in ascending ID order, and all validations/modifications are wrapped in a single transaction with READ_COMMITTED isolation. This ensures atomicity and correctness under concurrent load.

#### Improvements

Due to time constraints, a few enhancements were left out that would improve production readiness:

1. Transfer Record Pre-Persistence: Ideally, the initial transfer intent should be recorded in a separate early transaction to improve auditability and recoverability.

2. Retry Mechanism: Implementing automatic retries for failed transfers caused by transient issues like row lock timeouts would improve robustness under load.

3. Transfer Log Entity: A separate logging table for business events (e.g., status transitions, FX rate used, errors) would aid in traceability and compliance reporting.

### Testing
While I didn’t have enough time to fully complete the test suite, I’ve included key tests to verify core functionality:

1.  Happy path transfer — standard same-currency transfer.

2. FX conversion — verifies amount conversion and fee application.

3. Missing FX rate — ensures proper exception handling when no rate is available.

4. Concurrency test — verifies thread-safe behavior under concurrent transfers.
(Note: This test works but is currently messy and would benefit from a cleanup/refactor.)


## Addiontal Notes
H2 doesn't fully support row-level locking, even in SQL mode, so concurrency tests may be unreliable. A local PostgreSQL or MySQL instance is recommended for accurate testing.

