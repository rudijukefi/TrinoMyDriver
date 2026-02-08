package io.trino.driver;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class MyTrinoDriverTest {

    private final MyTrinoDriver driver = new MyTrinoDriver();

    @Test
    void acceptsURL_mytrinoPrefix_returnsTrue() throws SQLException {
        assertTrue(driver.acceptsURL("jdbc:mytrino://localhost:8080/"));
        assertTrue(driver.acceptsURL("jdbc:mytrino://host:8080/mysql/demo_db"));
    }

    @Test
    void acceptsURL_trinoPrefix_returnsTrue() throws SQLException {
        assertTrue(driver.acceptsURL("jdbc:trino://localhost:8080/"));
    }

    @Test
    void acceptsURL_otherPrefix_returnsFalse() throws SQLException {
        assertFalse(driver.acceptsURL("jdbc:mysql://localhost:3306/"));
        assertFalse(driver.acceptsURL("jdbc:trino://localhost:8080/".replace("trino", "other")));
    }

    @Test
    void acceptsURL_null_returnsFalse() throws SQLException {
        assertFalse(driver.acceptsURL(null));
    }

    @Test
    void connect_nullUrl_returnsNull() throws SQLException {
        assertNull(driver.connect(null, null));
    }

    @Test
    void connect_unacceptedUrl_returnsNull() throws SQLException {
        assertNull(driver.connect("jdbc:mysql://localhost:3306/", null));
    }
}
