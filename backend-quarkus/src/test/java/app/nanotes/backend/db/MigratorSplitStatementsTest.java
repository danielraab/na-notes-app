package app.nanotes.backend.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression test for the migration statement splitter: an apostrophe
 * inside a {@code --} line comment must not be mistaken for the start of a
 * string literal (which would swallow every remaining statement into one).
 */
class MigratorSplitStatementsTest {

    @SuppressWarnings("unchecked")
    private static List<String> split(String sql) throws Exception {
        Method m = Migrator.class.getDeclaredMethod("splitStatements", String.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(null, sql);
    }

    @Test
    void splitsOnTopLevelSemicolons() throws Exception {
        List<String> stmts = split("CREATE TABLE a (x TEXT); CREATE TABLE b (y TEXT);");
        assertEquals(2, stmts.size());
    }

    @Test
    void apostropheInsideStringLiteralDoesNotBreakSplitting() throws Exception {
        List<String> stmts = split("CREATE TABLE a (x TEXT CHECK (x IN ('read', 'edit'))); CREATE TABLE b (y TEXT);");
        assertEquals(2, stmts.size());
    }

    @Test
    void apostropheInsideLineCommentDoesNotBreakSplitting() throws Exception {
        String sql = """
                -- backend-go's own copy of this schema
                CREATE TABLE a (x TEXT);
                CREATE TABLE b (y TEXT);
                """;
        List<String> stmts = split(sql);
        assertEquals(2, stmts.size());
    }

    @Test
    void realMigrationFileSplitsIntoTenStatements() throws Exception {
        String sql = new String(
                MigratorSplitStatementsTest.class.getClassLoader().getResourceAsStream("db/migrations/0001_init.sql").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(10, split(sql).size());
    }
}
