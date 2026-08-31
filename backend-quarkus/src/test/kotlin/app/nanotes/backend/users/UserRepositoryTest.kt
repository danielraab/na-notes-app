package app.nanotes.backend.users

import app.nanotes.backend.db.Database
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class UserRepositoryTest {

    private lateinit var users: UserRepository

    @BeforeEach
    fun setUp(@TempDir tempDir: Path) {
        users = UserRepository(Database.forTesting(tempDir.resolve("users-test.db").toString()))
    }

    @Test
    fun upsertCreatesOnFirstLogin() {
        val u = users.upsertFromOidc("sub-1", "a@example.com", "Alice", "https://example.com/a.png")
        assertEquals("a@example.com", u.email)
        assertEquals("Alice", u.displayName)
        assertEquals("https://example.com/a.png", u.avatarUrl)

        val fetched = users.getById(u.id)
        assertEquals(u.id, fetched.id)
    }

    @Test
    fun upsertRefreshesProfileOnSubsequentLoginMatchedBySubject() {
        val first = users.upsertFromOidc("sub-1", "old@example.com", "Old Name", null)
        val second = users.upsertFromOidc("sub-1", "new@example.com", "New Name", "https://example.com/new.png")

        assertEquals(first.id, second.id, "same oidc_subject must resolve to the same account")
        assertEquals("new@example.com", second.email)
        assertEquals("New Name", second.displayName)
        assertEquals("https://example.com/new.png", second.avatarUrl)
    }

    @Test
    fun differentSubjectsCreateDifferentAccountsEvenWithSameEmailHistory() {
        val a = users.upsertFromOidc("sub-a", "shared@example.com", "A", null)
        val b = users.upsertFromOidc("sub-b", "other@example.com", "B", null)
        assertTrue(a.id != b.id)
    }

    @Test
    fun searchExcludesCallerAndMatchesPrefixCaseInsensitively() {
        val alice = users.upsertFromOidc("sub-1", "alice@example.com", "Alice Smith", null)
        users.upsertFromOidc("sub-2", "bob@example.com", "Bob Jones", null)
        users.upsertFromOidc("sub-3", "carol@example.com", "Carol King", null)

        val results = users.search(alice.id, "b", 10)
        assertEquals(1, results.size)
        assertEquals("Bob Jones", results[0].displayName)
    }

    @Test
    fun searchRespectsLimit() {
        for (i in 0 until 5) {
            users.upsertFromOidc("sub-$i", "match$i@example.com", "Match $i", null)
        }
        val results = users.search("nonexistent-caller-id", "match", 2)
        assertEquals(2, results.size)
    }
}
