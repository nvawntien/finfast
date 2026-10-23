package com.finfast;

import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** TEST-ONLY JDBC fault points on the SAME DataSource used by every participant.
 * Observes JDBC calls/delegate return, not server crash durability or an actual network partition.
 */
final class AllocationJdbcCommitProbe {
    enum Fault { NONE, BEFORE_COMMIT_DELEGATE, AFTER_COMMIT_DELEGATE, ROLLBACK }

    final List<Trace> transactions = new CopyOnWriteArrayList<>();
    final AtomicReference<Fault> nextFault = new AtomicReference<>(Fault.NONE);

    DataSource wrap(DataSource delegate) {
        return new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException { return observe(delegate.getConnection()); }
            @Override public Connection getConnection(String user, String password) throws SQLException {
                return observe(delegate.getConnection(user, password));
            }
        };
    }

    private Connection observe(Connection connection) {
        var trace = new Trace();
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> {
                    String name = method.getName();
                    if (name.equals("equals")) { return proxy == arguments[0]; }
                    if (name.equals("hashCode")) { return System.identityHashCode(proxy); }
                    if (name.equals("toString")) { return "ObservedTestPostgresConnection"; }
                    if (name.equals("setAutoCommit")) {
                        boolean enabled = (boolean) arguments[0];
                        trace.events.add("autocommit:" + enabled);
                        if (!enabled) {
                            trace.fault = nextFault.getAndSet(Fault.NONE);
                            transactions.add(trace);
                        }
                    } else if (name.equals("commit")) {
                        trace.events.add("commit:attempt");
                        trace.commitCalls++;
                        if (trace.fault == Fault.BEFORE_COMMIT_DELEGATE) {
                            throw new SQLException("synthetic commit failure before delegate call", "08006");
                        }
                        connection.commit();
                        trace.delegateCommitReturned = true;
                        trace.events.add("commit:delegate-returned");
                        if (trace.fault == Fault.AFTER_COMMIT_DELEGATE) {
                            throw new SQLException("synthetic lost commit acknowledgement after delegate returned", "08006");
                        }
                        return null;
                    } else if (name.equals("rollback")) {
                        trace.events.add("rollback:attempt");
                        trace.rollbackCalls++;
                        if (trace.fault == Fault.ROLLBACK) {
                            throw new SQLException("synthetic rollback failure before delegate call", "08006");
                        }
                        connection.rollback();
                        trace.delegateRollbackReturned = true;
                        trace.events.add("rollback:delegate-returned");
                        return null;
                    }
                    try { return method.invoke(connection, arguments); }
                    catch (InvocationTargetException error) { throw error.getCause(); }
                });
    }

    static final class Trace {
        final List<String> events = new CopyOnWriteArrayList<>();
        Fault fault = Fault.NONE;
        int commitCalls;
        int rollbackCalls;
        boolean delegateCommitReturned;
        boolean delegateRollbackReturned;
    }
}
