package com.demo.e2e;

import java.sql.*;
import java.util.Properties;

public class TrinoE2ERunner {
    public static void main(String[] args) {
        String host = System.getProperty("trino.e2e.host", "localhost");
        String port = System.getProperty("trino.e2e.port", "8080");
        
        // Use your custom driver's connection string format
        String url = String.format("jdbc:trino://%s:%s/mysql/demo_db", host, port);
        
        Properties properties = new Properties();
        properties.setProperty("user", "admin");

        System.out.println("Connecting to: " + url);

        // Retry logic inside Java for cleaner Docker logs
        boolean connected = false;
        for (int i = 0; i < 20; i++) {
            try (Connection conn = DriverManager.getConnection(url, properties)) {
                System.out.println("Connected to Trino successfully!");
                runTests(conn);
                connected = true;
                break;
            } catch (SQLException e) {
                System.out.println("Trino not ready yet... (Attempt " + (i + 1) + ")");
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            }
        }

        if (!connected) {
            System.err.println("Could not connect to Trino after 20 attempts.");
            System.exit(1);
        }
    }

    private static void runTests(Connection conn) throws SQLException {
        // Test 1: Standard ANSI Query
        String query1 = "SELECT * FROM users";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(query1)) {
            if (rs.next()) System.out.println("E2E Test 1 (Basic Select) Passed.");
        }

        // Test 2: ODBC Escape Query (Your driver should convert this via SqlParserLogic)
        String odbcQuery = "SELECT {fn CONCAT(name, ' - test')} FROM {oj users u LEFT JOIN users u2 ON u.id = u2.id} WHERE u.id = 1";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(odbcQuery)) {
            if (rs.next()) {
                System.out.println("E2E Test 2 (ODBC Escape Conversion) Passed.");
                System.out.println("Result: " + rs.getString(1));
            }
        }
    }
}