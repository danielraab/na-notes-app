package app.nanotes.backend.db

import app.nanotes.backend.config.AppConfig
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types
import java.util.Properties
import org.jboss.logging.Logger

/**
 * Owns the single JDBC connection and forward-only migrations (ADR 0006).
 * No other class opens a database connection directly.
 *
 * SQLite is single-writer, so — like backend-go's `SetMaxOpenConns(1)` —
 * every statement on this connection is serialized through [lock] rather
 * than pooled, avoiding `SQLITE_BUSY` under concurrent requests.
 */
@ApplicationScoped
class Database private constructor(databaseUrl: String) {

    @Inject
    constructor(config: AppConfig) : this(config.databaseUrl)

    private val connection: Connection = try {
        openSqlite(resolveSqlitePath(databaseUrl)).also { Migrator.migrate(it) }
    } catch (e: SQLException) {
        throw IllegalStateException("failed to open database", e)
    }

    private val lock = Any()

    @PreDestroy
    fun close() {
        try {
            connection.close()
        } catch (e: SQLException) {
            LOG.warn("failed to close database connection", e)
        }
    }

    fun interface RowMapper<T> {
        @Throws(SQLException::class)
        fun map(rs: ResultSet): T
    }

    fun <T> query(sql: String, mapper: RowMapper<T>, vararg args: Any?): List<T> {
        synchronized(lock) {
            try {
                connection.prepareStatement(sql).use { ps ->
                    bind(ps, args)
                    ps.executeQuery().use { rs ->
                        val out = mutableListOf<T>()
                        while (rs.next()) {
                            out.add(mapper.map(rs))
                        }
                        return out
                    }
                }
            } catch (e: SQLException) {
                throw DatabaseException("query failed: $sql", e)
            }
        }
    }

    fun <T> queryOne(sql: String, mapper: RowMapper<T>, vararg args: Any?): T? =
        query(sql, mapper, *args).firstOrNull()

    /** Executes an INSERT/UPDATE/DELETE and returns the number of affected rows. */
    fun update(sql: String, vararg args: Any?): Int {
        synchronized(lock) {
            try {
                connection.prepareStatement(sql).use { ps ->
                    bind(ps, args)
                    return ps.executeUpdate()
                }
            } catch (e: SQLException) {
                throw DatabaseException("update failed: $sql", e)
            }
        }
    }

    private fun bind(ps: PreparedStatement, args: Array<out Any?>) {
        args.forEachIndexed { i, arg ->
            if (arg == null) {
                ps.setNull(i + 1, Types.VARCHAR)
            } else {
                ps.setObject(i + 1, arg)
            }
        }
    }

    companion object {
        private val LOG: Logger = Logger.getLogger(Database::class.java)

        /** Only SQLite forms of DATABASE_URL are supported — see docs/decisions/0001-plain-jdbc-and-sqlite.md. */
        private const val POSTGRES_UNSUPPORTED =
            "DATABASE_URL points at PostgreSQL, which backend-quarkus does not support " +
                "(see docs/decisions/0001-plain-jdbc-and-sqlite.md) — use a SQLite path, " +
                "sqlite://<path>, or file:<path> instead"

        /** For tests only: bypasses AppConfig/CDI so repository tests can point at a throwaway temp file. */
        fun forTesting(databaseUrl: String): Database = Database(databaseUrl)

        private fun resolveSqlitePath(databaseUrl: String): String = when {
            databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://") ->
                throw IllegalStateException(POSTGRES_UNSUPPORTED)
            databaseUrl.startsWith("sqlite://") -> databaseUrl.removePrefix("sqlite://")
            databaseUrl.startsWith("file:") -> databaseUrl.removePrefix("file:")
            else -> databaseUrl
        }

        private fun openSqlite(path: String): Connection {
            File(path).parentFile?.mkdirs()
            val props = Properties().apply {
                setProperty("foreign_keys", "true")
                setProperty("busy_timeout", "5000")
            }
            val conn = DriverManager.getConnection("jdbc:sqlite:$path", props)
            // WAL lets readers proceed during a write; single-writer is enforced
            // by serializing all access through Database.lock instead of a pool.
            conn.createStatement().use { it.execute("PRAGMA journal_mode=WAL") }
            return conn
        }
    }
}
