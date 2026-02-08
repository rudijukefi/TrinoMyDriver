# TrinoMyDriver

A custom JDBC driver that extends the [Trino JDBC driver](https://trino.io/docs/current/client/jdbc.html) to intercept and transform SQL queries before execution. Use it to convert ODBC SQL dialect to standard ANSI SQL or apply custom parsing logic.

## Features

- **Custom URL prefix**: `jdbc:mytrino:` (delegates to `jdbc:trino:`); also accepts `jdbc:trino:` and wraps the connection
- **SQL interception**: All `prepareStatement`, `execute`, `executeQuery`, and `executeUpdate` calls are intercepted
- **ODBC to ANSI conversion**: Converts ODBC escape sequences using JSQLParser:
  - `{fn CONCAT(a,b)}` → `CONCAT(a,b)`
  - `{ts 'yyyy-mm-dd hh:mm:ss'}` → `TIMESTAMP '...'`
  - `{d 'yyyy-mm-dd'}` → `DATE '...'`
  - `{t 'hh:mm:ss'}` → `TIME '...'`
  - `{oj table1 LEFT OUTER JOIN table2 ON ...}` → standard ANSI join syntax
- **Logging**: Optional JDBC driver logging (same property style as Trino): `logLevel`, `logFile`; configurable via connection properties, URL parameters, or system properties

## Requirements

- Java 17+
- Maven 3.6+

## Build

```bash
mvn clean package
```

## Run Tests

**Unit tests only (default):**

```bash
mvn test
```

Runs all tests except those tagged `e2e`. No Trino server required.

**E2E tests** (require a running Trino, e.g. from `trino-mysql-demo`):

```bash
# Trino on localhost
mvn test -Pe2e -Dtrino.e2e.host=localhost

# Or set TRINO_E2E_HOST / TRINO_E2E_PORT
```

E2E tests connect with `jdbc:mytrino://...` and run queries against `mysql.demo_db.users`.

## Docker

**Build the driver image** (runs unit tests during build):

```bash
docker build -t trino-my-driver .
```

The packaged JAR is at `/app/trino-my-driver.jar` in the final image.

**Run Trino + MySQL + E2E tests** (`trino-mysql-demo`):

Starts MySQL, Trino with the MySQL catalog, and an E2E service that builds the driver and runs e2e tests against Trino using `jdbc:mytrino://trino:8080/mysql/demo_db`.

```bash
cd trino-mysql-demo
docker compose up --build e2e
```

- `mysql` – MySQL 8.0 with database `demo_db` and table `users` (see `mysql-init/init.sql`).
- `trino` – Trino with catalog `mysql` pointing at MySQL.
- `e2e` – Builds the project and runs only e2e tests (`mvn test -Pe2e`) after waiting for Trino.

To start only Trino and MySQL (no e2e): `docker compose up -d`. Then connect with `jdbc:mytrino://localhost:8080/mysql/demo_db`.

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

### Logging

The driver uses `java.util.logging` (same style as Trino). You can set **log level** and **log file** via connection properties, URL parameters, or system properties.

| Property   | Description | Example |
|-----------|-------------|--------|
| `logLevel` | JUL level: SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST | `FINE` |
| `logFile`  | Path to file where driver logs are appended | `C:\logs\driver.log` |

**Connection properties** (e.g. in DBeaver or `DriverManager.getConnection(url, props)`):

```java
props.setProperty("logLevel", "FINE");
props.setProperty("logFile", "C:\\logs\\mytrino-driver.log");
```

**URL parameters:** `logLevel` and `logFile` can appear in the query string. They are stripped before the URL is passed to Trino, so you can use Windows paths in the URL without URI errors:

```
jdbc:mytrino://localhost:8080/mysql/demo_db?logLevel=FINE&logFile=C:\logs\driver.log
```

**System properties** (process-wide):

```
-Dio.trino.driver.logLevel=FINE
-Dio.trino.driver.logFile=C:\logs\driver.log
```

**In code:** `DriverLogging.setLogFile(path)` and `DriverLogging.applyLevelFrom(url, info)` (called automatically in `connect()`).

### Custom Parsing

Implement your logic in `SqlParserLogic.parse(String sql)` in `io.trino.driver.SqlParserLogic`.

## Project Structure

```
src/main/java/io/trino/driver/
├── MyTrinoDriver.java          # Driver class (extends TrinoDriver)
├── MyTrinoConnectionProxy.java # Connection/Statement proxy with interception
├── SqlParserLogic.java         # ODBC→ANSI SQL conversion (JSQLParser)
└── DriverLogging.java          # Log level/file (logLevel, logFile; URL stripping)

src/main/resources/META-INF/services/
└── java.sql.Driver             # SPI registration for DriverManager

src/test/java/io/trino/driver/
├── SqlParserLogicTest.java     # Unit tests for SQL parsing
├── DriverLoggingTest.java      # Unit tests for URL stripping and logging props
├── MyTrinoDriverTest.java      # Unit tests for acceptsURL / connect
└── MyTrinoDriverE2ETest.java   # E2E tests (tag e2e; run with -Pe2e)

trino-mysql-demo/
├── docker-compose.yml          # mysql, trino, e2e services
├── Dockerfile.e2e              # Image that runs e2e tests against Trino
├── etc/catalog/mysql.properties # Trino MySQL connector config
└── mysql-init/init.sql         # demo_db.users seed data
```

## License

Same as upstream Trino (Apache 2.0).
