package app.nanotes.backend.db

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * Regression test for the migration statement splitter: an apostrophe
 * inside a `--` line comment must not be mistaken for the start of a
 * string literal (which would swallow every remaining statement into one).
 */
class MigratorSplitStatementsTest {

    @Test
    fun splitsOnTopLevelSemicolons() {
        val stmts = Migrator.splitStatements("CREATE TABLE a (x TEXT); CREATE TABLE b (y TEXT);")
        assertEquals(2, stmts.size)
    }

    @Test
    fun apostropheInsideStringLiteralDoesNotBreakSplitting() {
        val stmts = Migrator.splitStatements(
            "CREATE TABLE a (x TEXT CHECK (x IN ('read', 'edit'))); CREATE TABLE b (y TEXT);",
        )
        assertEquals(2, stmts.size)
    }

    @Test
    fun apostropheInsideLineCommentDoesNotBreakSplitting() {
        val sql = """
            -- backend-go's own copy of this schema
            CREATE TABLE a (x TEXT);
            CREATE TABLE b (y TEXT);
        """.trimIndent()
        assertEquals(2, Migrator.splitStatements(sql).size)
    }

    @Test
    fun realMigrationFileSplitsIntoTenStatements() {
        val sql = MigratorSplitStatementsTest::class.java.classLoader
            .getResourceAsStream("db/migrations/0001_init.sql")!!
            .readBytes()
            .toString(Charsets.UTF_8)
        assertEquals(10, Migrator.splitStatements(sql).size)
    }
}
