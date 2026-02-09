package com.demo.e2e;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class TrinoE2ERunner {
    static class TestCase {
        String description;
        String jdbcSql;
        String ansiSql;

        public TestCase(String description, String jdbcSql, String ansiSql) {
            this.description = description;
            this.jdbcSql = jdbcSql;
            this.ansiSql = ansiSql;
        }
    }

    public static void main(String[] args) {
        String host = System.getenv("TRINO_E2E_HOST");
        String port = System.getenv("TRINO_E2E_PORT");
        String user = "test_user";

        String myTrinoUrl = String.format("jdbc:mytrino://%s:%s/mysql/demo_db", host, port);
        String officialTrinoUrl = String.format("jdbc:trino://%s:%s/mysql/demo_db", host, port);

        List<TestCase> tests = new ArrayList<>();
        
        // 1. Sanity Check (Identical)
        tests.add(new TestCase(
            "System Tables Sanity Check",
            "SELECT type_name FROM system.jdbc.types ORDER BY type_name LIMIT 5",
            "SELECT type_name FROM system.jdbc.types ORDER BY type_name LIMIT 5"
        ));

        // 2. Date Literal Escape
        tests.add(new TestCase(
            "Date Literal Escape ({d})",
            "SELECT id, name, created_at FROM mysql.demo_db.users WHERE created_at >= {d '2020-01-01'}",
            "SELECT id, name, created_at FROM mysql.demo_db.users WHERE created_at >= DATE '2020-01-01'"
        ));

        // 3. Timestamp Literal Escape
        tests.add(new TestCase(
            "Timestamp Literal Escape ({ts})",
            "SELECT id, name FROM mysql.demo_db.users WHERE created_at <= {ts '2030-12-31 23:59:59'}",
            "SELECT id, name FROM mysql.demo_db.users WHERE created_at <= TIMESTAMP '2030-12-31 23:59:59'"
        ));

        // 4. Scalar Function Escape
        tests.add(new TestCase(
            "Scalar Function Escape ({fn UPPER} -> UPPER)",
            "SELECT {fn UPPER(name)} FROM mysql.demo_db.users ORDER BY id",
            "SELECT UPPER(name) FROM mysql.demo_db.users ORDER BY id"
        ));

        // 5. Nested Function Escape
        tests.add(new TestCase(
           "Nested Function Escape",
           "SELECT id FROM mysql.demo_db.users WHERE {fn LENGTH({fn UPPER(name)})} > 0 ORDER BY id",
           "SELECT id FROM mysql.demo_db.users WHERE LENGTH(UPPER(name)) > 0 ORDER BY id"
        ));
        
        // 6. Outer Join Escape
        // Note: Using self-join on users table for demonstration
        tests.add(new TestCase(
            "Outer Join Escape ({oj})",
            "SELECT u1.name, u2.name FROM {oj mysql.demo_db.users u1 LEFT OUTER JOIN mysql.demo_db.users u2 ON u1.id = u2.id} WHERE u1.id = 1",
            "SELECT u1.name, u2.name FROM mysql.demo_db.users u1 LEFT OUTER JOIN mysql.demo_db.users u2 ON u1.id = u2.id WHERE u1.id = 1"
        ));


        int maxAttempts = 30;
        int delaySeconds = 5;

        System.out.println("Starting E2E Paired Comparison Test Suite...");
        System.out.println("MyTrino URL: " + myTrinoUrl);
        System.out.println("Official Trino URL: " + officialTrinoUrl);

        // Wait for Trino to be ready using the official driver as a probe
        waitForTrino(officialTrinoUrl, user, maxAttempts, delaySeconds);

        boolean allPassed = true;

        for (TestCase test : tests) {
            System.out.println("\n--------------------------------------------------");
            System.out.println("Test Case: " + test.description);
            System.out.println("JDBC Query (MyDriver): " + test.jdbcSql);
            System.out.println("ANSI Query (Official): " + test.ansiSql);
            
            try {
                // 1. Get results from Official Driver (ANSI SQL)
                System.out.print("Expected (Official Driver)... ");
                long start = System.currentTimeMillis();
                List<List<Object>> officialResults = fetchResults(officialTrinoUrl, user, test.ansiSql);
                System.out.println("Done (" + (System.currentTimeMillis() - start) + "ms, " + officialResults.size() + " rows)");

                // 2. Get results from My Custom Driver (JDBC Escaped SQL)
                System.out.print("Actual (MyDriver)... ");
                start = System.currentTimeMillis();
                List<List<Object>> myResults = fetchResults(myTrinoUrl, user, test.jdbcSql);
                System.out.println("Done (" + (System.currentTimeMillis() - start) + "ms, " + myResults.size() + " rows)");

                // 3. Compare Results
                System.out.print("Comparing... ");
                compareResults(officialResults, myResults);
                System.out.println("PASSED");

            } catch (Exception e) {
                System.out.println("FAILED");
                e.printStackTrace();
                System.err.println("FAILED Test: " + test.description);
                System.err.println("Reason: " + e.getMessage());
                allPassed = false;
            }
        }

        System.out.println("\n--------------------------------------------------");
        if (allPassed) {
            System.out.println("All Tests PASSED. E2E Test Suite Successful.");
            System.exit(0);
        } else {
            System.err.println("Some Tests FAILED. E2E Test Suite Failed.");
            System.exit(1);
        }
    }

    private static void waitForTrino(String url, String user, int maxAttempts, int delaySeconds) {
        for (int i = 1; i <= maxAttempts; i++) {
            try (Connection conn = DriverManager.getConnection(url, user, null)) {
                Statement stmt = conn.createStatement();
                stmt.execute("SELECT 1");
                System.out.println("Trino is ready (Attempt " + i + ")");
                return;
            } catch (Exception e) {
                System.out.println("Attempt " + i + ": Trino not ready yet... (" + e.getMessage() + ")");
                try {
                    Thread.sleep(delaySeconds * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        System.err.println("Trino did not become ready after " + maxAttempts + " attempts.");
        System.exit(1);
    }

    private static List<List<Object>> fetchResults(String url, String user, String query) throws Exception {
        Properties props = new Properties();
        props.setProperty("user", user);
        
        // Ensure strictly mytrino driver is loaded for mytrino url if needed, 
        // though DriverManager should handle it if registered.
        
        try (Connection conn = DriverManager.getConnection(url, props);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            List<List<Object>> rows = new ArrayList<>();

            while (rs.next()) {
                List<Object> row = new ArrayList<>();
                for (int i = 1; i <= colCount; i++) {
                    row.add(rs.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        }
    }

    private static void compareResults(List<List<Object>> expected, List<List<Object>> actual) throws Exception {
        if (expected.size() != actual.size()) {
            throw new Exception("Row count mismatch! Expected: " + expected.size() + ", Actual: " + actual.size());
        }

        for (int i = 0; i < expected.size(); i++) {
            List<Object> expectedRow = expected.get(i);
            List<Object> actualRow = actual.get(i);

            if (expectedRow.size() != actualRow.size()) {
                throw new Exception("Column count mismatch at row " + i + "! Expected: " + expectedRow.size() + ", Actual: " + actualRow.size());
            }

            for (int j = 0; j < expectedRow.size(); j++) {
                Object expVal = expectedRow.get(j);
                Object actVal = actualRow.get(j);

                // Simple equality check. Might need more robust handling for types (e.g. Long vs Integer) if drivers differ
                if (!String.valueOf(expVal).equals(String.valueOf(actVal))) {
                     throw new Exception("Value mismatch at Row " + i + ", Col " + j + 
                        ". Expected: " + expVal + " (" + (expVal == null ? "null" : expVal.getClass().getSimpleName()) + ")" +
                        ", Actual: " + actVal + " (" + (actVal == null ? "null" : actVal.getClass().getSimpleName()) + ")");
                }
            }
        }
    }
}