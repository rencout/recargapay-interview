# Wallet Service

A Spring Boot application for managing digital wallets with transaction history and balance tracking. This service provides a robust API for wallet operations including deposits, withdrawals, transfers, and historical balance queries.

## Table of Contents

- [Installation Instructions](#installation-instructions)
- [How to Run the Service](#how-to-run-the-service)
- [How to Test the Service](#how-to-test-the-service)
- [Design Decisions](#design-decisions)
- [Compromises and Trade-offs](#compromises-and-trade-offs)
- [API Documentation](#api-documentation)
- [Database Schema](#database-schema)
- [Troubleshooting](#troubleshooting)

## Installation Instructions

### Prerequisites

- **Java 17** or higher
- **Gradle 8.5** or higher (included via Gradle Wrapper)
- **PostgreSQL 15** or higher
- **Docker** (for running tests with TestContainers)

### Environment Setup

1. **Install Java 17+**
   ```bash
   # On Windows (using Chocolatey)
   choco install openjdk17
   
   # On macOS (using Homebrew)
   brew install openjdk@17
   
   # On Ubuntu/Debian
   sudo apt update
   sudo apt install openjdk-17-jdk
   ```

2. **Install PostgreSQL 15+**
   ```bash
   # On Windows (using Chocolatey)
   choco install postgresql15
   
   # On macOS (using Homebrew)
   brew install postgresql@15
   
   # On Ubuntu/Debian
   sudo apt install postgresql-15
   ```

3. **Install Docker** (for testing)
   - Download from [Docker Desktop](https://www.docker.com/products/docker-desktop/)
   - Or use package managers:
     ```bash
     # On Windows/macOS: Use Docker Desktop
     # On Ubuntu
     sudo apt install docker.io docker-compose
     ```

### Database Setup

1. **Start PostgreSQL service**
   ```bash
   # Windows
   net start postgresql-x64-15
   
   # macOS
   brew services start postgresql@15
   
   # Ubuntu
   sudo systemctl start postgresql
   ```

2. **Create database and user**
   ```sql
   -- Connect to PostgreSQL as superuser
   psql -U postgres
   
   -- Create database
   CREATE DATABASE wallet_service;
   
   -- Create user (optional)
   CREATE USER wallet_user WITH PASSWORD 'your_password';
   GRANT ALL PRIVILEGES ON DATABASE wallet_service TO wallet_user;
   ```

3. **Configure environment variables**
   ```bash
   # Windows (PowerShell)
   $env:DB_USERNAME="wallet_user"
   $env:DB_PASSWORD="your_password"
   
   # macOS/Linux
   export DB_USERNAME=wallet_user
   export DB_PASSWORD=your_password
   ```

### Alternative: Using Docker Compose

For easier setup, you can use the provided Docker Compose file:

```bash
# Start PostgreSQL with Docker
docker-compose up -d postgres

# The database will be available at localhost:5432
# Default credentials: postgres/password
```

## How to Run the Service

### Option 1: Using Gradle (Recommended)

1. **Build the application**
   ```bash
   ./gradlew build
   ```

2. **Run the application**
   ```bash
   ./gradlew bootRun
   ```

3. **Access the application**
   - API: http://localhost:8080
   - Swagger UI: http://localhost:8080/swagger-ui.html

### Option 2: Using Docker

1. **Build Docker image**
   ```bash
   docker build -t wallet-service .
   ```
   
2. **Run with Docker Compose**
   ```bash
   docker-compose up
   ```

### Environment Configuration

The application uses the following configuration (from `application.yml`):

- **Database**: PostgreSQL at `localhost:5432/wallet_service`
- **Port**: 8080 (configurable via `server.port`)
- **Logging**: DEBUG level for application classes
- **API Documentation**: Swagger UI at `/swagger-ui.html`

## How to Test the Service

### Running Tests

1. **Run all tests**
   ```bash
   ./gradlew test
   ```

2. **Run only unit tests**
   ```bash
   ./gradlew test --tests "*Test"
   ```

3. **Run only integration tests**
   ```bash
   ./gradlew test --tests "*IntegrationTest"
   ```

4. **Run tests with coverage**
   ```bash
   ./gradlew test jacocoTestReport
   ```

### Test Dependencies

- **TestContainers**: Automatically starts PostgreSQL container for integration tests
- **JUnit 5**: Unit and integration testing framework
- **Mockito**: Mocking framework for unit tests

### Test Structure

- **Unit Tests**: `WalletServiceTest.java` - Tests business logic in isolation
- **Integration Tests**: `WalletControllerIntegrationTest.java` - Tests full HTTP endpoints
- **Test Configuration**: `application-test.yml` - Separate test database configuration

### Manual Testing

1. **Start the application**
   ```bash
   ./gradlew bootRun
   ```

2. **Use Swagger UI**
   - Navigate to http://localhost:8080/swagger-ui.html
   - Test endpoints directly from the browser

3. **Use curl commands**
   ```bash
   # Create a wallet
   curl -X POST http://localhost:8080/api/wallets \
     -H "Content-Type: application/json" \
     -d '{"userId": "user123"}'
   
   # Get balance
   curl http://localhost:8080/api/wallets/{walletId}/balance
   
   # Deposit funds
   curl -X POST http://localhost:8080/api/wallets/{walletId}/deposit \
     -H "Content-Type: application/json" \
     -d '{"amount": "100.00"}'
   ```

## Design Decisions

### Architecture Overview

The application follows a **layered architecture** with clear separation of concerns:

```
Controller Layer (REST API)
    ↓
Service Layer (Business Logic)
    ↓
Repository Layer (Data Access)
    ↓
Database Layer (PostgreSQL)
```

### Key Design Decisions

#### 1. **Technology Stack**
- **Spring Boot 3.2.0**: Modern, production-ready framework
- **Java 17**: Latest LTS version with enhanced performance
- **PostgreSQL**: ACID-compliant database for financial transactions
- **Flyway**: Database migration tool for version control
- **Gradle**: Modern build tool with dependency management

#### 2. **Concurrency Control**
- **Optimistic Locking**: Uses `@Version` annotation to prevent race conditions
- **Retry Mechanism**: Implements exponential backoff for concurrent updates
- **Transaction Isolation**: Uses `@Transactional` for atomic operations

#### 3. **Data Integrity**
- **Audit Trail**: Every operation creates a transaction record
- **Historical Queries**: Supports point-in-time balance queries
- **Validation**: Comprehensive input validation and business rule enforcement

#### 4. **API Design**
- **RESTful**: Follows REST principles with proper HTTP status codes
- **Swagger Documentation**: Auto-generated API documentation
- **DTO Pattern**: Separate request/response objects for API contracts

### Functional Requirements Implementation

#### ✅ **Wallet Management**
- Create wallets with unique user IDs
- Retrieve current balance with real-time accuracy
- Support for historical balance queries at any point in time

#### ✅ **Transaction Operations**
- **Deposits**: Add funds to wallet with balance validation
- **Withdrawals**: Remove funds with insufficient funds check
- **Transfers**: Atomic operations between wallets with rollback on failure

#### ✅ **Audit and Compliance**
- Complete transaction history with timestamps
- Balance tracking after each operation
- Support for regulatory compliance requirements

### Non-Functional Requirements Implementation

#### ✅ **Accuracy**
- **BigDecimal**: Precise decimal arithmetic for financial calculations
- **Optimistic Locking**: Prevents data corruption from concurrent updates
- **Transaction Rollback**: Ensures consistency on failures

#### ✅ **Auditability**
- **Transaction Records**: Every operation logged with metadata
- **Historical Queries**: Point-in-time balance reconstruction
- **Audit Fields**: Created/updated timestamps on all entities

#### ✅ **Scalability**
- **Connection Pooling**: HikariCP for efficient database connections
- **Indexed Queries**: Optimized database queries with proper indexing
- **Stateless Design**: Horizontal scaling capability

#### ✅ **Maintainability**
- **Clean Architecture**: Separation of concerns
- **Comprehensive Testing**: Unit and integration test coverage
- **Documentation**: Swagger API docs and code comments

## Compromises and Trade-offs

### Time Constraints and Simplifications

#### 1. **Authentication & Authorization**
- **Compromise**: No authentication/authorization implemented
- **Reason**: Focus on core business logic within time constraints
- **Future Improvement**: Implement JWT-based authentication with role-based access

#### 2. **Rate Limiting**
- **Compromise**: No rate limiting on API endpoints
- **Reason**: Core functionality prioritized over security features
- **Future Improvement**: Implement rate limiting with Redis or similar

#### 3. **Advanced Features**
- **Compromise**: No webhook notifications or event sourcing
- **Reason**: Scope limited to basic wallet operations
- **Future Improvement**: Implement event-driven architecture with Kafka

#### 4. **Performance Optimizations**
- **Compromise**: No caching layer implemented
- **Reason**: Focus on correctness over performance optimization
- **Future Improvement**: Add Redis caching for frequently accessed data

#### 5. **Monitoring & Observability**
- **Compromise**: Basic logging only, no metrics or tracing
- **Reason**: Core functionality prioritized
- **Future Improvement**: Add Prometheus metrics and distributed tracing

### Technical Trade-offs

#### 1. **Database Design**
- **Choice**: Single database with normalized schema
- **Trade-off**: Simpler deployment vs. potential scaling limitations
- **Alternative**: Microservices with separate databases per domain

#### 2. **Concurrency Strategy**
- **Choice**: Optimistic locking with retry mechanism
- **Trade-off**: Better performance vs. potential retry overhead
- **Alternative**: Pessimistic locking for critical operations

#### 3. **Historical Data**
- **Choice**: Transaction-based historical reconstruction
- **Trade-off**: Accurate but potentially slower queries
- **Alternative**: Separate historical balance table with snapshots

### What Could Be Improved with More Time

1. **Security Enhancements**
   - JWT-based authentication
   - Role-based access control
   - API rate limiting
   - Input sanitization and validation

2. **Performance Optimizations**
   - Redis caching layer
   - Database query optimization
   - Connection pooling tuning
   - Async processing for non-critical operations

3. **Operational Features**
   - Health check endpoints
   - Metrics and monitoring
   - Distributed tracing
   - Automated deployment pipelines

4. **Advanced Features**
   - Webhook notifications
   - Event sourcing architecture
   - Multi-currency support
   - Batch processing capabilities

## API Documentation

### Core Endpoints

#### Create Wallet
```http
POST /api/wallets
Content-Type: application/json

{
  "userId": "user123"
}
```

#### Get Current Balance
```http
GET /api/wallets/{walletId}/balance
```

#### Get Historical Balance
```http
GET /api/wallets/{walletId}/balance/history?timestamp=2024-01-01T10:00:00
```

#### Deposit Funds
```http
POST /api/wallets/{walletId}/deposit
Content-Type: application/json

{
  "amount": "100.00"
}
```

#### Withdraw Funds
```http
POST /api/wallets/{walletId}/withdraw
Content-Type: application/json

{
  "amount": "50.00"
}
```

#### Transfer Funds
```http
POST /api/wallets/transfer
Content-Type: application/json

{
  "sourceWalletId": "uuid1",
  "targetWalletId": "uuid2",
  "amount": "30.00"
}
```

### Interactive Documentation

Access the Swagger UI at: http://localhost:8080/swagger-ui.html

## Database Schema

### Wallets Table
```sql
CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    user_id VARCHAR NOT NULL,
    balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

### Transactions Table
```sql
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    wallet_id UUID REFERENCES wallets(id),
    type VARCHAR(20) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    balance_after DECIMAL(19,2) NOT NULL,
    related_wallet_id UUID REFERENCES wallets(id),
    created_at TIMESTAMP NOT NULL
);
```

## Troubleshooting

### Common Issues

#### 1. **Database Connection Error**
```bash
# Check PostgreSQL is running
sudo systemctl status postgresql

# Verify database exists
psql -U postgres -d wallet_service -c "SELECT 1;"

# Check connection settings in application.yml
```

#### 2. **Port Already in Use**
```bash
# Change port in application.yml
server:
  port: 8081

# Or kill process using port 8080
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

#### 3. **Test Failures**
```bash
# Ensure Docker is running
docker --version

# Check TestContainers can start PostgreSQL
docker run --rm postgres:15-alpine echo "Docker works"
```

#### 4. **Build Failures**
```bash
# Clean and rebuild
./gradlew clean build

# Check Java version
java -version

# Verify Gradle wrapper
./gradlew --version
```

### Logs and Debugging

```bash
# Enable debug logging
logging:
  level:
    com.recargapay.walletservice: DEBUG
    org.springframework.transaction: DEBUG

# View application logs
./gradlew bootRun --debug
```

## License

This project is licensed under the MIT License.
