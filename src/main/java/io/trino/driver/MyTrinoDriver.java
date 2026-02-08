package io.trino.driver;

import io.trino.jdbc.TrinoDriver;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Custom JDBC driver that extends the Trino JDBC driver.
 * Accepts URLs with the {@code jdbc:mytrino:} prefix and intercepts
 * all SQL queries for custom parsing before delegating to the underlying driver.
 */
public class MyTrinoDriver extends TrinoDriver {

    public static final String URL_PREFIX = "jdbc:mytrino:";
    private static final String TRINO_URL_PREFIX = "jdbc:trino:";

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        // Accept both our prefix and Trino's so that when super.connect(trinoUrl, info)
        // calls this.acceptsURL(trinoUrl), it returns true and the parent proceeds.
        return url != null && (url.startsWith(URL_PREFIX) || url.startsWith(TRINO_URL_PREFIX));
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        String trinoUrl = url.startsWith(URL_PREFIX) ? url.replace(URL_PREFIX, TRINO_URL_PREFIX) : url;
        Connection connection = super.connect(trinoUrl, info);
        if (connection == null) {
            return null;
        }
        return MyTrinoConnectionProxy.wrap(connection);
    }
}
