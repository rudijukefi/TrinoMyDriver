package io.trino.driver;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * Creates JDBC proxy wrappers for Connection and Statement to intercept
 * SQL and apply custom parsing before delegating to the underlying Trino driver.
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
                new Class<?>[]{Connection.class},
                new ConnectionHandler(delegate)
        );
    }

    /**
     * Wraps a Statement or PreparedStatement in a proxy that intercepts
     * execute, executeQuery, and executeUpdate calls.
     */
    static Object wrapStatement(Statement delegate) {
        Class<?>[] interfaces = delegate instanceof PreparedStatement
                ? new Class<?>[]{PreparedStatement.class}
                : new Class<?>[]{Statement.class};
        return Proxy.newProxyInstance(
                delegate.getClass().getClassLoader(),
                interfaces,
                new StatementHandler(delegate)
        );
    }

    private static final class ConnectionHandler implements InvocationHandler {
        private final Connection delegate;

        ConnectionHandler(Connection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (PREPARE_STATEMENT.equals(method.getName()) && args != null && args.length >= 1 && args[0] instanceof String sql) {
                args = args.clone();
                args[0] = SqlParserLogic.parse(sql);
            }
            Object result = method.invoke(delegate, args);
            if (result instanceof Statement stmt) {
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
                    && args != null && args.length >= 1 && args[0] instanceof String sql) {
                args = args.clone();
                args[0] = SqlParserLogic.parse(sql);
            }
            return method.invoke(delegate, args);
        }
    }
}
