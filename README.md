# Wallet Service

A Spring Boot application for managing digital wallets with transaction history and balance tracking.

## Features

- Create and manage digital wallets
- Deposit and withdraw funds
- Transfer funds between wallets
- Historical balance tracking
- Optimistic locking for concurrency control
- Comprehensive transaction audit trail
- RESTful API with Swagger documentation

## Technology Stack

- **Java 17**
- **Spring Boot 3.2.0**
- **Spring Data JPA**
- **PostgreSQL**
- **Flyway** (Database migrations)
- **Gradle** (Build tool)
- **Lombok** (Reduces boilerplate code)
- **Swagger/OpenAPI** (API documentation)
- **TestContainers** (Integration testing)

## Prerequisites

- Java 17 or higher
- Gradle 8.5 or higher
- PostgreSQL 15 or higher
- Docker (for running tests with TestContainers)

## Database Setup

1. Create a PostgreSQL database:
```sql
CREATE DATABASE wallet_service;
```

2. Create a user (optional, you can use the default postgres user):
```sql
CREATE USER wallet_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE wallet_service TO wallet_user;
```

3. Configure the database connection in `application.yml` or set environment variables:
```bash
export DB_USERNAME=wallet_user
export DB_PASSWORD=your_password
```

## Building and Running

### Prerequisites Check
Ensure you have the following installed:
- Java 17 or higher: `java -version`
- Docker and Docker Compose: `docker --version` and `docker-compose --version`

### Start the Database
```bash
# Start PostgreSQL database using Docker
docker-compose up -d

# Verify the database is running
docker-compose ps
```

### Set Environment Variables (Optional)
The application uses default database credentials, but you can override them:
```bash
# On Unix/Linux/macOS
export DB_USERNAME=postgres
export DB_PASSWORD=password

# On Windows PowerShell
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="password"

# On Windows Command Prompt
set DB_USERNAME=postgres
set DB_PASSWORD=password
```

### Build the Application
```bash
# On Unix/Linux/macOS
./gradlew build

# On Windows
./gradlew.bat build
```

### Run the Application
```bash
# On Unix/Linux/macOS
./gradlew bootRun

# On Windows
./gradlew.bat bootRun
```

The application will start on `http://localhost:8080`

### Verify the Application
1. Check if the application is running: `http://localhost:8080/actuator/health` (if actuator is added)
2. Access Swagger UI: `http://localhost:8080/swagger-ui/index.html`
3. View API documentation: `http://localhost:8080/api-docs`

### Run Tests
```bash
# Run all tests
./gradlew test

# On Windows
./gradlew.bat test

# Run only unit tests
./gradlew test --tests "*Test"

# Run only integration tests
./gradlew test --tests "*IntegrationTest"
```

## API Documentation

Once the application is running, you can access the Swagger UI at:
`http://localhost:8080/swagger-ui/index.html`

### API Endpoints

#### Create Wallet
- **POST** `/api/wallets`
- **Description**: Creates a new wallet for a user
- **Request Body**:
```json
{
  "userId": "user123"
}
```
- **Response**:
```json
{
  "id": "uuid",
  "userId": "user123",
  "balance": "0.00",
  "createdAt": "2024-01-01T10:00:00",
  "updatedAt": "2024-01-01T10:00:00"
}
```

#### Get Current Balance
- **GET** `/api/wallets/{walletId}/balance`
- **Description**: Retrieves the current balance of a wallet
- **Response**:
```json
{
  "walletId": "uuid",
  "balance": "100.00",
  "balanceAfter": "100.00"
}
```

#### Get Historical Balance
- **GET** `/api/wallets/{walletId}/balance/history?timestamp=2024-01-01T10:00:00`
- **Description**: Retrieves the balance of a wallet at a specific point in time
- **Parameters**:
  - `timestamp`: ISO 8601 formatted datetime
- **Response**:
```json
{
  "walletId": "uuid",
  "balance": "75.00",
  "balanceAfter": "75.00"
}
```

#### Deposit Funds
- **POST** `/api/wallets/{walletId}/deposit`
- **Description**: Deposits funds into a wallet
- **Request Body**:
```json
{
  "amount": "50.00"
}
```
- **Response**:
```json
{
  "walletId": "uuid",
  "balance": "150.00",
  "balanceAfter": "150.00"
}
```

#### Withdraw Funds
- **POST** `/api/wallets/{walletId}/withdraw`
- **Description**: Withdraws funds from a wallet
- **Request Body**:
```json
{
  "amount": "25.00"
}
```
- **Response**:
```json
{
  "walletId": "uuid",
  "balance": "125.00",
  "balanceAfter": "125.00"
}
```

#### Transfer Funds
- **POST** `/api/transfer`
- **Description**: Transfers funds between two wallets
- **Request Body**:
```json
{
  "sourceWalletId": "uuid1",
  "targetWalletId": "uuid2",
  "amount": "30.00"
}
```
- **Response**: 200 OK (no body)

## Database Schema

### Wallets Table
- `id` (UUID, Primary Key)
- `user_id` (VARCHAR, Not Null)
- `balance` (DECIMAL(19,2), Not Null, Default 0.00)
- `version` (BIGINT, Not Null, Default 0) - Optimistic locking
- `created_at` (TIMESTAMP, Not Null)
- `updated_at` (TIMESTAMP, Not Null)

### Transactions Table
- `id` (UUID, Primary Key)
- `wallet_id` (UUID, Foreign Key to wallets.id)
- `type` (VARCHAR(20), Not Null) - DEPOSIT, WITHDRAW, TRANSFER_IN, TRANSFER_OUT
- `amount` (DECIMAL(19,2), Not Null)
- `balance_after` (DECIMAL(19,2), Not Null)
- `related_wallet_id` (UUID, Nullable, Foreign Key to wallets.id)
- `created_at` (TIMESTAMP, Not Null)

## Key Features

### Concurrency Control
- Uses optimistic locking with version field
- Retry mechanism for handling concurrent updates
- Prevents race conditions in balance updates

### Transaction Audit Trail
- Every operation creates a transaction record
- Tracks balance after each operation
- Supports historical balance queries

### Error Handling
- Global exception handler with proper HTTP status codes
- Validation for request parameters
- Custom exceptions for business logic errors

### Testing
- Unit tests for service layer
- Integration tests with TestContainers
- Comprehensive test coverage

## Development

### Project Structure
```
src/
├── main/
│   ├── java/com/recargapay/walletservice/
│   │   ├── config/
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── exception/
│   │   ├── repository/
│   │   └── service/
│   └── resources/
│       ├── db/migration/
│       └── application.yml
└── test/
    └── java/com/recargapay/walletservice/
        ├── controller/
        └── service/
```

### Adding New Features
1. Create entity classes in the `entity` package
2. Create DTOs in the `dto` package
3. Create repository interfaces in the `repository` package
4. Implement business logic in the `service` package
5. Create REST endpoints in the `controller` package
6. Add appropriate tests
7. Create Flyway migration if needed

## Troubleshooting

### Common Issues

1. **Database Connection Error**
   - Ensure PostgreSQL is running
   - Check database credentials in `application.yml`
   - Verify database exists

2. **Port Already in Use**
   - Change the port in `application.yml`:
   ```yaml
   server:
     port: 8081
   ```

3. **Test Failures**
   - Ensure Docker is running (required for TestContainers)
   - Check that PostgreSQL container can be started

## License

This project is licensed under the MIT License.
