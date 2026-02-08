package io.trino.driver;

import java.io.IOException;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * JDBC driver logging using the same style as the Trino driver (java.util.logging).
 * Log level and log file can be set via connection properties or system properties.
 * <p>
 * Connection properties (same mechanism as Trino connection params, URL or Properties):
 * <ul>
 *   <li>{@code logLevel} – JUL level: SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST</li>
 *   <li>{@code logFile} – path to log file (driver logs are appended to this file)</li>
 * </ul>
 * System properties (process-wide defaults):
 * <ul>
 *   <li>{@code io.trino.driver.logLevel} – same values as logLevel</li>
 *   <li>{@code io.trino.driver.logFile} – path to log file</li>
 * </ul>
 */
public final class DriverLogging {

    /** Logger name for the driver (package-based, same convention as Trino). */
    public static final String LOGGER_NAME = "io.trino.driver";

    /** Connection property key for log level (Trino-style: URL or Properties). */
    public static final String PROP_LOG_LEVEL = "logLevel";

    /** Connection property key for log file path (Trino-style: URL or Properties). */
    public static final String PROP_LOG_FILE = "logFile";

    private static final Logger LOG = Logger.getLogger(LOGGER_NAME);

    private static volatile String currentLogFile;
    private static volatile FileHandler fileHandler;

    private DriverLogging() {
    }

    /**
     * Returns the driver logger. Use this for all driver log calls.
     */
    public static Logger getLogger() {
        return LOG;
    }

    /**
     * Applies log level and log file from connection URL, properties, and system properties (same style as Trino).
     * Call this at connection entry point (e.g. in connect()).
     *
     * @param url  connection URL (may contain logLevel=, logFile= in query string; may be null)
     * @param info connection properties (may be null)
     */
    public static void applyLevelFrom(String url, Properties info) {
        String levelName = getProperty(PROP_LOG_LEVEL, url, info, LOGGER_NAME + ".logLevel");
        if (levelName != null && !levelName.isEmpty()) {
            Level level = parseLevel(levelName);
            if (level != null) {
                LOG.setLevel(level);
            }
        }
        String logPath = getProperty(PROP_LOG_FILE, url, info, LOGGER_NAME + ".logFile");
        if (logPath != null && !logPath.isEmpty()) {
            setLogFile(logPath);
        }
    }

    private static String getProperty(String key, String url, Properties info, String sysKey) {
        if (info != null && info.containsKey(key)) {
            return info.getProperty(key);
        }
        if (url != null) {
            String fromUrl = getParamFromUrl(url, key);
            if (fromUrl != null) return fromUrl;
        }
        return System.getProperty(sysKey);
    }

    /**
     * Sets the log file path. Driver log messages are appended to this file.
     * Can be called from {@code applyLevelFrom} (via logFile property) or directly.
     *
     * @param path absolute or relative path to the log file (e.g. {@code /var/log/mytrino.log} or {@code C:\logs\mytrino.log})
     */
    public static void setLogFile(String path) {
        if (path == null || path.isEmpty()) return;
        String normalized = path.trim();
        if (normalized.equals(currentLogFile)) return;
        synchronized (DriverLogging.class) {
            if (normalized.equals(currentLogFile)) return;
            if (fileHandler != null) {
                LOG.removeHandler(fileHandler);
                fileHandler.close();
                fileHandler = null;
                currentLogFile = null;
            }
            try {
                FileHandler handler = new FileHandler(normalized, true);
                handler.setFormatter(new SimpleFormatter());
                handler.setLevel(LOG.getLevel() != null ? LOG.getLevel() : Level.ALL);
                LOG.addHandler(handler);
                fileHandler = handler;
                currentLogFile = normalized;
            } catch (IOException e) {
                LOG.warning("Could not open log file '" + normalized + "': " + e.getMessage());
            }
        }
    }

    /** Extracts a query parameter from URL (e.g. ?logLevel=FINE or &logFile=/tmp/driver.log). */
    static String getParamFromUrl(String url, String paramKey) {
        int q = url.indexOf('?');
        if (q < 0) return null;
        String query = url.substring(q + 1);
        for (String param : query.split("&")) {
            int eq = param.indexOf('=');
            if (eq > 0 && paramKey.equalsIgnoreCase(param.substring(0, eq).trim())) {
                return param.substring(eq + 1).trim();
            }
        }
        return null;
    }

    /**
     * Removes driver-only query parameters (logLevel, logFile) from the URL.
     * Call this before passing the URL to the Trino driver, so it never sees
     * invalid characters (e.g. backslash in logFile=C:\logs\driver.log) or unknown params.
     *
     * @param url full JDBC URL possibly containing logLevel= and logFile=
     * @return URL with our params stripped (safe for Trino's URI parser)
     */
    public static String stripDriverParamsFromUrl(String url) {
        if (url == null) return null;
        int q = url.indexOf('?');
        if (q < 0) return url;
        String base = url.substring(0, q);
        String query = url.substring(q + 1);
        StringBuilder rest = new StringBuilder();
        for (String param : query.split("&")) {
            int eq = param.indexOf('=');
            String key = (eq > 0) ? param.substring(0, eq).trim() : param.trim();
            if (key.isEmpty()) continue;
            if (PROP_LOG_LEVEL.equalsIgnoreCase(key) || PROP_LOG_FILE.equalsIgnoreCase(key)) {
                continue; // strip our params
            }
            if (rest.length() > 0) rest.append('&');
            rest.append(param);
        }
        if (rest.length() == 0) return base;
        return base + '?' + rest;
    }

    private static Level parseLevel(String name) {
        if (name == null) return null;
        String n = name.trim().toUpperCase();
        switch (n) {
            case "SEVERE": return Level.SEVERE;
            case "WARNING": return Level.WARNING;
            case "INFO": return Level.INFO;
            case "CONFIG": return Level.CONFIG;
            case "FINE": return Level.FINE;
            case "FINER": return Level.FINER;
            case "FINEST": return Level.FINEST;
            case "OFF": return Level.OFF;
            case "ALL": return Level.ALL;
            default: return null;
        }
    }
}
