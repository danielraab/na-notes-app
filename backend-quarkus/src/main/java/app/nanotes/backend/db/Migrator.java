package app.nanotes.backend.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Forward-only migration runner (ADR 0006): applies any
 * {@code db/migrations/*.sql} file listed in {@code db/migrations/index.txt}
 * that isn't already recorded in {@code schema_migrations}, in order,
 * statement-by-statement inside one transaction per file.
 */
final class Migrator {

    private Migrator() {}

    static void migrate(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS schema_migrations (name TEXT PRIMARY KEY, applied_at TEXT NOT NULL)");
        }

        for (String name : readIndex()) {
            if (alreadyApplied(connection, name)) {
                continue;
            }
            String sql = readResource("db/migrations/" + name);
            connection.setAutoCommit(false);
            try {
                try (Statement st = connection.createStatement()) {
                    for (String stmt : splitStatements(sql)) {
                        st.execute(stmt);
                    }
                }
                try (PreparedStatement ps =
                        connection.prepareStatement("INSERT INTO schema_migrations (name, applied_at) VALUES (?, ?)")) {
                    ps.setString(1, name);
                    ps.setString(2, Timestamps.now());
                    ps.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw new SQLException("apply migration " + name, e);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static boolean alreadyApplied(Connection connection, String name) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM schema_migrations WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private static List<String> readIndex() {
        List<String> names = new ArrayList<>();
        try (InputStream in = Migrator.class.getClassLoader().getResourceAsStream("db/migrations/index.txt")) {
            if (in == null) {
                throw new IllegalStateException("db/migrations/index.txt not found on classpath");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.strip();
                    if (!line.isEmpty()) {
                        names.add(line);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to read migrations index", e);
        }
        return names;
    }

    private static String readResource(String path) {
        try (InputStream in = Migrator.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("migration resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read migration: " + path, e);
        }
    }

    /**
     * Splits a migration file's SQL text into individual statements on
     * top-level {@code ;} boundaries (i.e. not inside a string literal or a
     * {@code --} line comment — an apostrophe in a comment, e.g. "user's",
     * must not be mistaken for the start of a string literal). Migration
     * files in this codebase don't use dollar-quoting or put {@code ;}
     * inside identifiers, so this is otherwise a simple split.
     */
    static List<String> splitStatements(String sqlText) {
        List<String> stmts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inString = false;
        int n = sqlText.length();
        int i = 0;
        while (i < n) {
            char c = sqlText.charAt(i);
            if (!inString && c == '-' && i + 1 < n && sqlText.charAt(i + 1) == '-') {
                int eol = sqlText.indexOf('\n', i);
                if (eol < 0) {
                    eol = n;
                }
                cur.append(sqlText, i, eol);
                i = eol;
                continue;
            }
            if (c == '\'') {
                inString = !inString;
                cur.append(c);
            } else if (c == ';' && !inString) {
                String s = cur.toString().strip();
                if (!s.isEmpty()) {
                    stmts.add(s);
                }
                cur.setLength(0);
            } else {
                cur.append(c);
            }
            i++;
        }
        String s = cur.toString().strip();
        if (!s.isEmpty()) {
            stmts.add(s);
        }
        return stmts;
    }
}
