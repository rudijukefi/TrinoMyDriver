package io.trino.driver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlParserLogicTest {

    @Test
    void parse_nullReturnsNull() {
        assertNull(SqlParserLogic.parse(null));
    }

    @Test
    void parse_tsEscape_convertsToTimestamp() {
        String sql = "SELECT * FROM t WHERE created = {ts '2024-01-15 10:30:00'}";
        String result = SqlParserLogic.parse(sql);
        assertNotNull(result);
        assertTrue(result.contains("TIMESTAMP '2024-01-15 10:30:00'"));
        assertFalse(result.contains("{ts "));
    }

    @Test
    void parse_dEscape_convertsToDate() {
        String sql = "SELECT * FROM t WHERE dt = {d '2024-01-15'}";
        String result = SqlParserLogic.parse(sql);
        assertNotNull(result);
        assertTrue(result.contains("DATE '2024-01-15'"));
        assertFalse(result.contains("{d "));
    }

    @Test
    void parse_fnEscape_unwrapsFunction() {
        String sql = "SELECT {fn CONCAT(first_name, last_name)} FROM users";
        String result = SqlParserLogic.parse(sql);
        assertNotNull(result);
        assertTrue(result.contains("CONCAT"));
        assertFalse(result.contains("{fn "));
    }

    @Test
    void parse_ojEscape_unwrapsOuterJoin() {
        String sql = "SELECT * FROM {oj t1 LEFT OUTER JOIN t2 ON t1.id = t2.id}";
        String result = SqlParserLogic.parse(sql);
        assertNotNull(result);
        assertTrue(result.contains("LEFT OUTER JOIN"));
        assertFalse(result.contains("{oj "));
    }

    @Test
    void parse_simpleSelect_unchanged() {
        String sql = "SELECT id, name FROM customers WHERE id = 1";
        String result = SqlParserLogic.parse(sql);
        assertNotNull(result);
        assertTrue(result.contains("SELECT"));
        assertTrue(result.contains("customers"));
    }
}
