package app.nanotes.backend.db

import java.sql.Connection
import java.sql.SQLException

/**
 * Forward-only migration runner (ADR 0006): applies any `*.sql` file under
 * `db/migrations/` that's listed in `db/migrations/index.txt` and isn't
 * already recorded in `schema_migrations`, in order, statement-by-statement
 * inside one transaction per file.
 */
internal object Migrator {

    fun migrate(connection: Connection) {
        connection.createStatement().use {
            it.execute("CREATE TABLE IF NOT EXISTS schema_migrations (name TEXT PRIMARY KEY, applied_at TEXT NOT NULL)")
        }

        for (name in readIndex()) {
            if (alreadyApplied(connection, name)) continue

            val sql = readResource("db/migrations/$name")
            connection.autoCommit = false
            try {
                connection.createStatement().use { st ->
                    for (stmt in splitStatements(sql)) {
                        st.execute(stmt)
                    }
                }
                connection.prepareStatement("INSERT INTO schema_migrations (name, applied_at) VALUES (?, ?)").use { ps ->
                    ps.setString(1, name)
                    ps.setString(2, Timestamps.now())
                    ps.executeUpdate()
                }
                connection.commit()
            } catch (e: SQLException) {
                connection.rollback()
                throw SQLException("apply migration $name", e)
            } finally {
                connection.autoCommit = true
            }
        }
    }

    private fun alreadyApplied(connection: Connection, name: String): Boolean {
        connection.prepareStatement("SELECT COUNT(*) FROM schema_migrations WHERE name = ?").use { ps ->
            ps.setString(1, name)
            ps.executeQuery().use { rs ->
                rs.next()
                return rs.getInt(1) > 0
            }
        }
    }

    private fun readIndex(): List<String> {
        val stream = javaClass.classLoader.getResourceAsStream("db/migrations/index.txt")
            ?: throw IllegalStateException("db/migrations/index.txt not found on classpath")
        return stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
    }

    private fun readResource(path: String): String {
        val stream = javaClass.classLoader.getResourceAsStream(path)
            ?: throw IllegalStateException("migration resource not found: $path")
        return stream.readBytes().toString(Charsets.UTF_8)
    }

    /**
     * Splits a migration file's SQL text into individual statements on
     * top-level `;` boundaries (i.e. not inside a string literal or a `--`
     * line comment — an apostrophe in a comment, e.g. "user's", must not be
     * mistaken for the start of a string literal). Migration files in this
     * codebase don't use dollar-quoting or put `;` inside identifiers, so
     * this is otherwise a simple split.
     */
    internal fun splitStatements(sqlText: String): List<String> {
        val stmts = mutableListOf<String>()
        val cur = StringBuilder()
        var inString = false
        var i = 0
        val n = sqlText.length
        while (i < n) {
            val c = sqlText[i]
            if (!inString && c == '-' && i + 1 < n && sqlText[i + 1] == '-') {
                var eol = sqlText.indexOf('\n', i)
                if (eol < 0) eol = n
                cur.append(sqlText, i, eol)
                i = eol
                continue
            }
            if (c == '\'') {
                inString = !inString
                cur.append(c)
            } else if (c == ';' && !inString) {
                val s = cur.toString().trim()
                if (s.isNotEmpty()) stmts.add(s)
                cur.setLength(0)
            } else {
                cur.append(c)
            }
            i++
        }
        val s = cur.toString().trim()
        if (s.isNotEmpty()) stmts.add(s)
        return stmts
    }
}
