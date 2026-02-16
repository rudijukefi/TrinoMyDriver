package io.trino.driver;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.logging.Level;

/**
 * Creates JDBC proxy wrappers for Connection and Statement to intercept
 * SQL and apply custom parsing before delegating to the underlying Trino
 * driver.
 */
public final class MyTrinoConnectionProxy {

    private static final String PREPARE_STATEMENT = "prepareStatement";
    private static final String EXECUTE = "execute";
    private static final String EXECUTE_QUERY = "executeQuery";
    private static final String EXECUTE_UPDATE = "executeUpdate";

    private MyTrinoConnectionProxy() {
        // Utility class - prevent instantiation
    }

    /**
     * Wraps a Connection in a proxy that intercepts prepareStatement calls.
     *
     * @param delegate the actual Connection from the Trino driver
     * @return a proxied Connection that modifies SQL before delegation
     */
    public static Connection wrap(Connection delegate) {
        return (Connection) Proxy.newProxyInstance(
                delegate.getClass().getClassLoader(),
                new Class<?>[] { Connection.class },
                new ConnectionHandler(delegate));
    }

    /**
     * Wraps a Statement or PreparedStatement in a proxy that intercepts
     * execute, executeQuery, and executeUpdate calls.
     */
    static Object wrapStatement(Statement delegate) {
        Class<?>[] interfaces = delegate instanceof PreparedStatement
                ? new Class<?>[] { PreparedStatement.class }
                : new Class<?>[] { Statement.class };
        return Proxy.newProxyInstance(
                delegate.getClass().getClassLoader(),
                interfaces,
                new StatementHandler(delegate));
    }

    private static final class ConnectionHandler implements InvocationHandler {
        private final Connection delegate;

        ConnectionHandler(Connection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (PREPARE_STATEMENT.equals(method.getName()) && args != null && args.length >= 1
                    && args[0] instanceof String) {
                String sql = (String) args[0];
                if (DriverLogging.getLogger().isLoggable(Level.FINE)) {
                    DriverLogging.getLogger().fine("prepareStatement(original): " + truncate(sql));
                }
                args = args.clone();
                String parsed = SqlParserLogic.parse(sql);
                args[0] = parsed;
                if (DriverLogging.getLogger().isLoggable(Level.FINE) && !parsed.equals(sql)) {
                    DriverLogging.getLogger().fine("prepareStatement(parsed): " + truncate(parsed));
                }
            }
            Object result = method.invoke(delegate, args);
            if (result instanceof Statement) {
                Statement stmt = (Statement) result;
                return wrapStatement(stmt);
            }
            return result;
        }
    }

    private static final class StatementHandler implements InvocationHandler {
        private final Statement delegate;

        StatementHandler(Statement delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if ((EXECUTE.equals(methodName) || EXECUTE_QUERY.equals(methodName) || EXECUTE_UPDATE.equals(methodName))
                    && args != null && args.length >= 1 && args[0] instanceof String) {
                String sql = (String) args[0];
                if (DriverLogging.getLogger().isLoggable(Level.FINE)) {
                    DriverLogging.getLogger().fine(methodName + "(original): " + truncate(sql));
                }
                args = args.clone();
                String parsed = SqlParserLogic.parse(sql);
                args[0] = parsed;
                if (DriverLogging.getLogger().isLoggable(Level.FINE) && !parsed.equals(sql)) {
                    DriverLogging.getLogger().fine(methodName + "(parsed): " + truncate(parsed));
                }
            }
            return method.invoke(delegate, args);
        }
    }

    private static String truncate(String sql) {
        if (sql == null)
            return "null";
        return sql.length() > 200 ? sql.substring(0, 200) + "..." : sql;
    }
}
