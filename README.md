# PayFlow API

A Spring Boot REST API that powers a simplified UPI-style digital wallet system. Users can register, view their wallet balance, and record money transfers between each other — entirely through HTTP calls.

---

## How to Run the App

### Prerequisites
- Java 17 or above
- Maven installed (or use the Maven wrapper `./mvnw`)
- IntelliJ IDEA or any Java IDE

### Steps

1. Clone the repository:
   ```
   git clone https://github.com/vikashinikaruppusamyk/payflow-api.git
   ```

2. Navigate to the project directory:
   ```
   cd payflow-api
   ```

3. Run the application using Maven:
   ```
   mvn spring-boot:run
   ```

4. The application will start on port 8080. You should see:
   ```
   Started PayflowApplication in X seconds
   ```

5. Access the H2 console at:
   ```
   http://localhost:8080/h2-console
   ```
    - JDBC URL: `jdbc:h2:mem:payflow`
    - Username: `sa`
    - Password: (leave empty)

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /users | Register a new user |
| GET | /users | Get all registered users |
| GET | /users/{id} | Get a user by their ID |
| GET | /users/upi/{upiId} | Get a user by their UPI ID |
| GET | /users/balance/{amount} | Get users with balance >= amount |
| POST | /transactions | Record a money transfer |

---

## Project Structure

The project is organized into four packages, each with a specific responsibility:

### entity
Contains the JPA entity classes that map to database tables. Each entity represents a real-world concept:
- `User.java` — mapped to the `app_user` table. Holds user details like name, UPI ID, balance, and phone number.
- `Transaction.java` — mapped to the `transaction` table. Records every money transfer with sender UPI ID, receiver UPI ID, amount, and an optional note.

### repository
Contains interfaces that extend `JpaRepository`. Spring Data JPA automatically provides implementations for standard database operations like save, findAll, findById, and delete — without writing any SQL manually.
- `UserRepository.java` — includes a derived query `findByUpiId` and a custom JPQL query to find users by balance.
- `TransactionRepository.java` — inherits standard CRUD operations from JpaRepository.

### service
Contains the business logic layer. Service classes sit between the controller and the repository. They receive requests from controllers, apply any business rules, and call the appropriate repository methods.
- `UserService.java` — handles user registration, fetching users by ID, by UPI ID, and by balance.
- `TransactionService.java` — handles saving a transaction record.

### controller
Contains REST controllers that expose HTTP endpoints. Each controller receives an HTTP request, calls the appropriate service method, and returns the response as JSON.
- `UserController.java` — handles all `/users` endpoints.
- `TransactionController.java` — handles the `/transactions` endpoint.

---

## Spring Boot Features in PayFlow

### 1. Embedded Server (Tomcat)
Spring Boot ships with an embedded Tomcat server. In PayFlow, this means the application starts as a standalone Java process on port 8080 — no separate server installation required. When you run `mvn spring-boot:run`, Tomcat starts automatically inside the application itself.

### 2. Auto-Configuration
Spring Boot detects the dependencies in the classpath and automatically configures the necessary beans. In PayFlow:
- It detects `spring-boot-starter-data-jpa` and automatically sets up Hibernate and the JPA EntityManagerFactory.
- It detects the H2 dependency and automatically configures an in-memory database connection.
- It enables the H2 console automatically when `spring.h2.console.enabled=true` is set.
- No XML configuration files or manual bean definitions were needed.

### 3. Production-Ready Defaults (Opinionated Defaults)
Spring Boot provides sensible defaults so developers can start building immediately. In PayFlow:
- The default port is 8080 — no need to configure it manually.
- Spring Data JPA automatically creates the `app_user` and `transaction` tables on startup using the entity class definitions.
- The `spring.jpa.ddl-auto` strategy defaults to `create-drop` for in-memory databases, meaning tables are created fresh on every startup.

---

## Repository Layer — Custom Queries

### findByUpiId — Derived Query Method

```java
User findByUpiId(String upiId);
```

The SQL JPA generates for this method:
```sql
SELECT * FROM app_user WHERE upi_id = ?
```

**How JPA derives it from the method name:**
Spring Data JPA reads the method name and breaks it into parts. `findBy` tells JPA it is a SELECT query. `UpiId` tells JPA which field to filter on. JPA then looks at the `User` entity, finds the field `upiId`, maps it to the column `upi_id`, and generates the SQL automatically. No manual SQL is needed.

**What the `?` placeholder means:**
The `?` is a positional parameter placeholder. When the method is called with an actual value (e.g., `"priya@okaxis"`), JPA substitutes that value in place of `?` before executing the query. This is a prepared statement — it prevents SQL injection and improves performance.

---

## Custom Query Approaches — Comparison

### 1. Derived Method Names
```java
User findByUpiId(String upiId);
```
JPA reads the method name and generates the SQL automatically. Simple, readable, and requires no extra annotations. Best for simple single-field lookups.

### 2. @Query with JPQL
```java
@Query("SELECT u FROM User u WHERE u.balance >= :amount")
List<User> findByBalanceGreaterThanEqual(@Param("amount") Double amount);
```
JPQL (Java Persistence Query Language) uses entity class names and field names — not table or column names. This means the query is database-independent. If the underlying database changes (e.g., from H2 to MySQL), the JPQL query still works without modification.

### 3. Native SQL
```java
@Query(value = "SELECT * FROM app_user WHERE balance >= :amount", nativeQuery = true)
List<User> findByBalanceNative(@Param("amount") Double amount);
```

**Why native queries are the least preferred:**
Native SQL queries are tightly coupled to the specific database being used. If the database changes, the query must be rewritten. They also bypass JPA's entity mapping, meaning you lose the type safety and abstraction that JPA provides. Additionally, native queries use actual table and column names, which means any schema rename breaks the query. JPQL and derived method names are always preferred because they work across different databases and remain consistent with the entity model.