package io.trino.driver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DriverLoggingTest {

    @Test
    void stripDriverParamsFromUrl_nullReturnsNull() {
        assertNull(DriverLogging.stripDriverParamsFromUrl(null));
    }

    @Test
    void stripDriverParamsFromUrl_noQueryReturnsSame() {
        String url = "jdbc:trino://localhost:8080/";
        assertEquals(url, DriverLogging.stripDriverParamsFromUrl(url));
    }

    @Test
    void stripDriverParamsFromUrl_stripsLogLevelOnly() {
        String url = "jdbc:trino://localhost:8080/?logLevel=FINE";
        assertEquals("jdbc:trino://localhost:8080/", DriverLogging.stripDriverParamsFromUrl(url));
    }

    @Test
    void stripDriverParamsFromUrl_stripsLogFileOnly() {
        String url = "jdbc:trino://localhost:8080/?logFile=C:\\logs\\driver.log";
        assertEquals("jdbc:trino://localhost:8080/", DriverLogging.stripDriverParamsFromUrl(url));
    }

    @Test
    void stripDriverParamsFromUrl_stripsBothKeepsOthers() {
        String url = "jdbc:trino://localhost:8080/?user=test&logLevel=FINE&logFile=/tmp/d.log&SSL=false";
        assertEquals("jdbc:trino://localhost:8080/?user=test&SSL=false", DriverLogging.stripDriverParamsFromUrl(url));
    }

    @Test
    void stripDriverParamsFromUrl_allDriverParamsLeavesBase() {
        String url = "jdbc:mytrino://host:8080/mysql/demo?logLevel=INFO&logFile=/var/log/driver.log";
        assertEquals("jdbc:mytrino://host:8080/mysql/demo", DriverLogging.stripDriverParamsFromUrl(url));
    }

    @Test
    void getParamFromUrl_returnsValue() {
        assertEquals("FINE", DriverLogging.getParamFromUrl("jdbc:trino://localhost:8080/?logLevel=FINE", DriverLogging.PROP_LOG_LEVEL));
        assertEquals("FINE", DriverLogging.getParamFromUrl("jdbc:trino://localhost:8080/?logLevel=FINE&user=a", DriverLogging.PROP_LOG_LEVEL));
    }

    @Test
    void getParamFromUrl_noQueryReturnsNull() {
        assertNull(DriverLogging.getParamFromUrl("jdbc:trino://localhost:8080/", DriverLogging.PROP_LOG_LEVEL));
    }

    @Test
    void getParamFromUrl_missingKeyReturnsNull() {
        assertNull(DriverLogging.getParamFromUrl("jdbc:trino://localhost:8080/?user=test", DriverLogging.PROP_LOG_LEVEL));
    }

    @Test
    void applyLevelFrom_doesNotThrow() {
        assertDoesNotThrow(() -> DriverLogging.applyLevelFrom(null, null));
        assertDoesNotThrow(() -> DriverLogging.applyLevelFrom("jdbc:mytrino://localhost:8080/", new java.util.Properties()));
    }
}
