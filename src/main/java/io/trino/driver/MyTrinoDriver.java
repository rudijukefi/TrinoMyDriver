package io.trino.driver;

import io.trino.jdbc.TrinoDriver;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;

/**
 * Custom JDBC driver that extends the Trino JDBC driver.
 * Accepts URLs with the {@code jdbc:mytrino:} prefix and intercepts
 * all SQL queries for custom parsing before delegating to the underlying driver.
 * <p>
 * Logging uses the same property style as the Trino driver (connection Properties or URL params).
 * Set {@code logLevel} to a JUL level (e.g. FINE, FINER, FINEST) to enable driver logs.
 */
public class MyTrinoDriver extends TrinoDriver {

    public static final String URL_PREFIX = "jdbc:mytrino:";
    private static final String TRINO_URL_PREFIX = "jdbc:trino:";

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        // Accept both our prefix and Trino's so that when super.connect(trinoUrl, info)
        // calls this.acceptsURL(trinoUrl), it returns true and the parent proceeds.
        boolean accept = url != null && (url.startsWith(URL_PREFIX) || url.startsWith(TRINO_URL_PREFIX));
        if (accept && DriverLogging.getLogger().isLoggable(Level.FINER)) {
            DriverLogging.getLogger().finer("acceptsURL(" + url + ") -> true");
        }
        return accept;
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        DriverLogging.applyLevelFrom(url, info);
        if (!acceptsURL(url)) {
            if (DriverLogging.getLogger().isLoggable(Level.FINE)) {
                DriverLogging.getLogger().fine("connect(" + url + ") -> null (URL not accepted)");
            }
            return null;
        }
        String trinoUrl = url.startsWith(URL_PREFIX) ? url.replace(URL_PREFIX, TRINO_URL_PREFIX) : url;
        trinoUrl = DriverLogging.stripDriverParamsFromUrl(trinoUrl);
        if (DriverLogging.getLogger().isLoggable(Level.INFO)) {
            DriverLogging.getLogger().info("Connecting: " + url + " -> " + trinoUrl);
        }
        Connection connection = super.connect(trinoUrl, info);
        if (connection == null) {
            if (DriverLogging.getLogger().isLoggable(Level.FINE)) {
                DriverLogging.getLogger().fine("connect(" + url + ") -> null (Trino driver returned null)");
            }
            return null;
        }
        Connection wrapped = MyTrinoConnectionProxy.wrap(connection);
        if (DriverLogging.getLogger().isLoggable(Level.INFO)) {
            DriverLogging.getLogger().info("Connection established (wrapped for SQL parsing)");
        }
        return wrapped;
    }
}
