package com.demo.e2e;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class TrinoE2ERunner {
    public static void main(String[] args) {
        String host = System.getenv("TRINO_E2E_HOST");
        String port = System.getenv("TRINO_E2E_PORT");
        // Ensure your driver JAR is in the classpath and uses the correct prefix
        String url = String.format("jdbc:mytrino://%s:%s/mysql/demo_db", host, port);
        String user = "test_user";

        int maxAttempts = 30;
        int delaySeconds = 5;

        System.out.println("Starting E2E Test: Connecting to " + url);

        for (int i = 1; i <= maxAttempts; i++) {
            try (Connection conn = DriverManager.getConnection(url, user, null)) {
                System.out.println("Successfully connected to Trino on attempt " + i);
                
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT 1 as connection_test");
                
                if (rs.next()) {
                    System.out.println("Query successful! Result: " + rs.getInt("connection_test"));
                    System.out.println("E2E PASSED");
                    System.exit(0); 
                }
            } catch (Exception e) {
                System.out.println("Attempt " + i + ": Trino not ready yet... (" + e.getMessage() + ")");
                try {
                    Thread.sleep(delaySeconds * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        System.err.println("E2E FAILED: Could not connect to Trino after " + maxAttempts + " attempts.");
        System.exit(1);
    }
}