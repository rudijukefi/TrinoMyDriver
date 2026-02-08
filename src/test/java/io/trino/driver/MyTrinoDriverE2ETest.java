package io.trino.driver;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end tests against a real Trino server (e.g. the one started by trino-mysql-demo docker-compose).
 * Run only when Trino is available: set TRINO_E2E_HOST (default trino) and optionally TRINO_E2E_PORT (default 8080).
 * In docker-compose e2e service these are set so the container runs these tests after Trino is up.
 */
@Tag("e2e")
class MyTrinoDriverE2ETest {

    private static final String E2E_HOST = System.getenv("TRINO_E2E_HOST");
    private static final String E2E_PORT = System.getenv("TRINO_E2E_PORT");
    private static final String HOST = (E2E_HOST != null && !E2E_HOST.isEmpty()) ? E2E_HOST : System.getProperty("trino.e2e.host", "trino");
    private static final int PORT = parsePort(E2E_PORT != null ? E2E_PORT : System.getProperty("trino.e2e.port", "8080"));

    private static int parsePort(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 8080;
        }
    }

    private static String jdbcUrl() {
        return "jdbc:mytrino://" + HOST + ":" + PORT + "/mysql/demo_db";
    }

    @BeforeAll
    static void assumeTrinoConfigured() {
        boolean envSet = E2E_HOST != null && !E2E_HOST.isEmpty();
        boolean propSet = System.getProperty("trino.e2e.host") != null && !System.getProperty("trino.e2e.host").isEmpty();
        assumeTrue(envSet || propSet, "Set TRINO_E2E_HOST or -Dtrino.e2e.host to run e2e tests");
    }

    @Test
    void connect_andQueryUsers_returnsRows() throws SQLException {
        String url = jdbcUrl();
        Properties props = new Properties();
        props.setProperty("user", "test");

        try (Connection conn = DriverManager.getConnection(url, props);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, name, email FROM users ORDER BY id")) {

            assertTrue(rs.next());
            assertEquals(1, rs.getInt("id"));
            assertEquals("Alice", rs.getString("name"));
            assertEquals("alice@example.com", rs.getString("email"));

            assertTrue(rs.next());
            assertEquals(2, rs.getInt("id"));
            assertEquals("Bob", rs.getString("name"));

            assertTrue(rs.next());
            assertEquals(3, rs.getInt("id"));
            assertEquals("Charlie", rs.getString("name"));

            assertFalse(rs.next());
        }
    }

    @Test
    void connect_odbcEscapeInSql_parsedAndExecuted() throws SQLException {
        String url = jdbcUrl();
        Properties props = new Properties();
        props.setProperty("user", "test");

        try (Connection conn = DriverManager.getConnection(url, props);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM users WHERE created_at >= {ts '2020-01-01 00:00:00'} ORDER BY id")) {

            assertTrue(rs.next());
            assertEquals("Alice", rs.getString(1));
            assertTrue(rs.next());
            assertTrue(rs.next());
            assertFalse(rs.next());
        }
    }

    @Test
    void connect_preparedStatement_works() throws SQLException {
        String url = jdbcUrl();
        Properties props = new Properties();
        props.setProperty("user", "test");

        try (Connection conn = DriverManager.getConnection(url, props);
             PreparedStatement ps = conn.prepareStatement("SELECT name FROM users WHERE id = ?")) {

            ps.setInt(1, 2);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Bob", rs.getString(1));
                assertFalse(rs.next());
            }
        }
    }

    @Test
    void connect_metadata_catalogAndSchema() throws SQLException {
        String url = jdbcUrl();
        Properties props = new Properties();
        props.setProperty("user", "test");

        try (Connection conn = DriverManager.getConnection(url, props)) {
            DatabaseMetaData meta = conn.getMetaData();
            assertNotNull(meta.getCatalogTerm());
            assertNotNull(meta.getURL());
            assertTrue(meta.getURL().contains("trino") || meta.getURL().contains("mytrino"));
        }
    }
}
