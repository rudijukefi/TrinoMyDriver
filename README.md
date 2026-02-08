# TrinoMyDriver

A custom JDBC driver that extends the [Trino JDBC driver](https://trino.io/docs/current/client/jdbc.html) to intercept and transform SQL queries before execution. Use it to convert ODBC SQL dialect to standard ANSI SQL or apply custom parsing logic.

## Features

- **Custom URL prefix**: `jdbc:mytrino:` (delegates to `jdbc:trino:`)
- **SQL interception**: All `prepareStatement`, `execute`, `executeQuery`, and `executeUpdate` calls are intercepted
- **ODBC to ANSI conversion**: Converts ODBC escape sequences using JSQLParser:
  - `{fn CONCAT(a,b)}` → `CONCAT(a,b)`
  - `{ts 'yyyy-mm-dd hh:mm:ss'}` → `TIMESTAMP '...'`
  - `{d 'yyyy-mm-dd'}` → `DATE '...'`
  - `{t 'hh:mm:ss'}` → `TIME '...'`
  - `{oj table1 LEFT OUTER JOIN table2 ON ...}` → standard ANSI join syntax

## Requirements

- Java 17+
- Maven 3.6+

## Build

```bash
mvn clean package
```

## Run Tests

```bash
mvn test
```

## Docker (Build & Test)

```bash
docker build -t trino-my-driver .
```

The image runs `mvn clean test` during build. The packaged JAR is available at `/app/trino-my-driver.jar` in the final image.

## Usage

### JDBC URL

```
jdbc:mytrino://localhost:8080/catalog/schema
```

### Java Example

```java
Properties props = new Properties();
props.setProperty("user", "your_user");

Connection conn = DriverManager.getConnection(
    "jdbc:mytrino://host:8080/catalog/schema",
    props
);

// SQL is intercepted and transformed before reaching Trino
PreparedStatement ps = conn.prepareStatement(
    "SELECT {fn CONCAT(name, '-')} FROM users WHERE created = {ts '2024-01-01 00:00:00'}"
);
ResultSet rs = ps.executeQuery();
```

### Custom Parsing

Implement your logic in `SqlParserLogic.parse(String sql)` in `io.trino.driver.SqlParserLogic`.

## Project Structure

```
src/main/java/io/trino/driver/
├── MyTrinoDriver.java          # Driver class (extends TrinoDriver)
├── MyTrinoConnectionProxy.java # Connection/Statement proxy with interception
└── SqlParserLogic.java         # ODBC→ANSI SQL conversion (JSQLParser)

src/main/resources/META-INF/services/
└── java.sql.Driver             # SPI registration for DriverManager
```

## License

Same as upstream Trino (Apache 2.0).
