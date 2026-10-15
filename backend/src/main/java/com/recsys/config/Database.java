package com.recsys.config;

import com.recsys.exception.DataAccessException;
import com.recsys.exception.RecSysException;
import org.h2.jdbcx.JdbcConnectionPool;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Thin JDBC gateway: owns the connection pool and offers two safe ways to run SQL.
 * <ul>
 *   <li>{@link #read} - auto-commit connection for queries / single statements</li>
 *   <li>{@link #inTransaction} - commits on success, rolls back on ANY failure</li>
 * </ul>
 * Connections are always returned to the pool (try-with-resources).
 */
public class Database implements AutoCloseable {

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    private final JdbcConnectionPool pool;

    public Database(String jdbcUrl) {
        this.pool = JdbcConnectionPool.create(jdbcUrl, "sa", "");
        this.pool.setMaxConnections(20);
    }

    public <T> T read(SqlFunction<T> work) {
        try (Connection c = pool.getConnection()) {
            return work.apply(c);
        } catch (SQLException e) {
            throw new DataAccessException("Database operation failed", e);
        }
    }

    public <T> T inTransaction(SqlFunction<T> work) {
        try (Connection c = pool.getConnection()) {
            c.setAutoCommit(false);
            try {
                T result = work.apply(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                if (e instanceof RecSysException re) throw re;
                if (e instanceof RuntimeException re) throw re;
                throw new DataAccessException("Transaction rolled back", e);
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database connection failed", e);
        }
    }

    /** True if the exception chain contains a SQL unique-constraint violation (SQLState 23505). */
    public static boolean isUniqueViolation(Throwable t) {
        for (Throwable x = t; x != null; x = x.getCause()) {
            if (x instanceof SQLException s && "23505".equals(s.getSQLState())) return true;
        }
        return false;
    }

    @Override
    public void close() {
        pool.dispose();
    }
}
