package io.trino.driver;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SqlParserLogicTest {

    // --- Basic Escapes ---

    @Test
    void parse_ojEscape_unwrapsOuterJoin() {
        String sql = "SELECT * FROM {oj t1 LEFT OUTER JOIN t2 ON t1.id = t2.id}";
        String result = SqlParserLogic.parse(sql);
        assertNotNull(result);
        assertTrue(result.toUpperCase().contains("LEFT OUTER JOIN"));
        assertFalse(result.contains("{oj"));
        assertFalse(result.contains("}"));
    }

    @Test
    void parse_fnEscape_unwrapsFunction() {
        String sql = "SELECT {fn UCASE(name)} FROM usage";
        String result = SqlParserLogic.parse(sql);
        assertNotNull(result);
        assertTrue(result.toUpperCase().contains("UCASE(NAME)"));
        assertFalse(result.contains("{fn"));
        assertFalse(result.contains("}"));
    }

    @Test
    void parse_dEscape_convertsToDate() {
        String sql = "SELECT * FROM t WHERE date_col = {d '2023-10-25'}";
        String result = SqlParserLogic.parse(sql);
        assertNotNull(result);
        assertTrue(result.toUpperCase().contains("DATE '2023-10-25'"));
        assertFalse(result.contains("{d"));
        assertFalse(result.contains("}"));
    }

    @Test
    void parse_tsEscape_convertsToTimestamp() {
        String sql = "SELECT * FROM t WHERE ts_col = {ts '2023-10-25 12:34:56'}";
        String result = SqlParserLogic.parse(sql);
        assertNotNull(result);
        assertTrue(result.toUpperCase().contains("TIMESTAMP '2023-10-25 12:34:56'"));
        assertFalse(result.contains("{ts"));
        assertFalse(result.contains("}"));
    }

    // --- Nested Escapes ---

    @Test
    void parse_nestedFnInsideFn_unwrapsBoth() {
        // {fn CONCAT({fn UCASE(a)}, b)}
        String sql = "SELECT {fn CONCAT({fn UCASE(col1)}, col2)} FROM table";
        String result = SqlParserLogic.parse(sql);
        assertNotNull(result);
        // Expect CONCAT(UCASE(col1), col2)
        String upperResult = result.toUpperCase();
        assertTrue(upperResult.contains("CONCAT") && upperResult.contains("UCASE"));
        assertFalse(result.contains("{fn"));
        assertFalse(result.contains("}"));
    }

    @Test
    void parse_dateInsideFn_convertsCorrectly() {
        // {fn WEEK({d '2005-01-24'})}
        String sql = "SELECT {fn WEEK({d '2005-01-24'})} FROM t";
        String result = SqlParserLogic.parse(sql);
        assertNotNull(result);
        // Expect WEEK(DATE '2005-01-24')
        String upperResult = result.toUpperCase();
        assertTrue(upperResult.contains("WEEK"));
        assertTrue(upperResult.contains("DATE '2005-01-24'"));
        assertFalse(result.contains("{fn"));
        assertFalse(result.contains("{d"));
        assertFalse(result.contains("}"));
    }

    // --- Subquery Escapes ---

    @Test
    void parse_escapesInFromSubquery_convertsCorrectly() {
        String sql = "SELECT * FROM (SELECT {fn ABS(score)} FROM scores) AS sub";
        String result = SqlParserLogic.parse(sql);
        assertNotNull(result);
        assertTrue(result.toUpperCase().contains("ABS(SCORE)"));
        assertFalse(result.contains("{fn"));
    }

    @Test
    void parse_escapesInWhereSubquery_convertsCorrectly() {
        String sql = "SELECT * FROM t WHERE date_col > (SELECT MAX({d '2020-01-01'}) FROM other)";
        String result = SqlParserLogic.parse(sql);
        assertNotNull(result);
        assertTrue(result.toUpperCase().contains("DATE '2020-01-01'"));
        assertFalse(result.contains("{d"));
    }

    // --- Complex Recursion ---

    @Test
    void parse_deeplyNestedComplexRecursion_handlesStack() {
        // Nested: {fn A({oj B ... {fn C({d ...})} ... })}
        // Constructing a complex fake query to test recursion
        String sql = "SELECT {fn CONCAT(val, {fn SUBSTRING((SELECT {fn MAX(x)} FROM {oj t1 JOIN t2 ON t1.id=t2.id} WHERE d > {d '2020-01-01'}), 1, 5)})} FROM table";
        
        String result = SqlParserLogic.parse(sql);
        assertNotNull(result);
        String upper = result.toUpperCase();

        // Check for all unwrapped elements
        assertTrue(upper.contains("CONCAT"), "Missing CONCAT");
        assertTrue(upper.contains("SUBSTRING"), "Missing SUBSTRING");
        assertTrue(upper.contains("MAX(X)"), "Missing MAX");
        assertTrue(upper.contains("JOIN"), "Missing JOIN"); // t1 JOIN t2
        assertTrue(upper.contains("DATE '2020-01-01'"), "Missing DATE literal");

        // Verify clean up
        assertFalse(result.contains("{fn"), "Leftover {fn");
        assertFalse(result.contains("{oj"), "Leftover {oj");
        assertFalse(result.contains("{d"), "Leftover {d");
        assertFalse(result.contains("}"), "Leftover }");
    }

    @Test
    void parse_nullReturnsNull() {
        assertNull(SqlParserLogic.parse(null));
    }
}
