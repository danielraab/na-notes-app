package app.nanotes.backend.db;

import app.nanotes.backend.config.AppConfig;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.jboss.logging.Logger;

/**
 * Owns the single JDBC connection and forward-only migrations (ADR 0006).
 * No other class opens a database connection directly.
 *
 * <p>SQLite is single-writer, so — like backend-go's
 * {@code SetMaxOpenConns(1)} — every statement on this connection is
 * serialized through {@link #lock} rather than pooled, avoiding
 * {@code SQLITE_BUSY} under concurrent requests.
 */
@ApplicationScoped
public class Database {

    private static final Logger LOG = Logger.getLogger(Database.class);

    /** Only SQLite forms of DATABASE_URL are supported — see docs/decisions/0001-plain-jdbc-and-sqlite.md. */
    private static final String POSTGRES_UNSUPPORTED =
            "DATABASE_URL points at PostgreSQL, which backend-quarkus does not support "
                    + "(see docs/decisions/0001-plain-jdbc-and-sqlite.md) — use a SQLite path, "
                    + "sqlite://<path>, or file:<path> instead";

    private final Connection connection;
    private final Object lock = new Object();

    @Inject
    public Database(AppConfig config) {
        this(config.databaseUrl());
    }

    private Database(String databaseUrl) {
        try {
            this.connection = openSqlite(resolveSqlitePath(databaseUrl));
            Migrator.migrate(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to open database", e);
        }
    }

    /** For tests only: bypasses AppConfig/CDI so repository tests can point a throwaway temp file. */
    public static Database forTesting(String databaseUrl) {
        return new Database(databaseUrl);
    }

    @PreDestroy
    void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            LOG.warn("failed to close database connection", e);
        }
    }

    private static String resolveSqlitePath(String databaseUrl) {
        if (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://")) {
            throw new IllegalStateException(POSTGRES_UNSUPPORTED);
        }
        if (databaseUrl.startsWith("sqlite://")) {
            return databaseUrl.substring("sqlite://".length());
        }
        if (databaseUrl.startsWith("file:")) {
            return databaseUrl.substring("file:".length());
        }
        return databaseUrl;
    }

    private static Connection openSqlite(String path) throws SQLException {
        java.io.File file = new java.io.File(path);
        java.io.File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Properties props = new Properties();
        props.setProperty("foreign_keys", "true");
        props.setProperty("busy_timeout", "5000");
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path, props);
        try (Statement st = conn.createStatement()) {
            // WAL lets readers proceed during a write; single-writer is enforced
            // by serializing all access through Database.lock instead of a pool.
            st.execute("PRAGMA journal_mode=WAL");
        }
        return conn;
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind(ps, args);
                try (ResultSet rs = ps.executeQuery()) {
                    List<T> out = new ArrayList<>();
                    while (rs.next()) {
                        out.add(mapper.map(rs));
                    }
                    return out;
                }
            } catch (SQLException e) {
                throw new DatabaseException("query failed: " + sql, e);
            }
        }
    }

    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... args) {
        List<T> rows = query(sql, mapper, args);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Executes an INSERT/UPDATE/DELETE and returns the number of affected rows. */
    public int update(String sql, Object... args) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind(ps, args);
                return ps.executeUpdate();
            } catch (SQLException e) {
                throw new DatabaseException("update failed: " + sql, e);
            }
        }
    }

    private static void bind(PreparedStatement ps, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                ps.setNull(i + 1, java.sql.Types.VARCHAR);
            } else {
                ps.setObject(i + 1, args[i]);
            }
        }
    }
}
