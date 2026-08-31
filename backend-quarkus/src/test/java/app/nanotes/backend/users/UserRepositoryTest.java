package app.nanotes.backend.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.nanotes.backend.db.Database;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserRepositoryTest {

    private UserRepository users;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        users = new UserRepository(Database.forTesting(tempDir.resolve("users-test.db").toString()));
    }

    @Test
    void upsertCreatesOnFirstLogin() {
        User u = users.upsertFromOidc("sub-1", "a@example.com", "Alice", "https://example.com/a.png");
        assertEquals("a@example.com", u.email());
        assertEquals("Alice", u.displayName());
        assertEquals("https://example.com/a.png", u.avatarUrl());

        User fetched = users.getById(u.id());
        assertEquals(u.id(), fetched.id());
    }

    @Test
    void upsertRefreshesProfileOnSubsequentLoginMatchedBySubject() {
        User first = users.upsertFromOidc("sub-1", "old@example.com", "Old Name", null);
        User second = users.upsertFromOidc("sub-1", "new@example.com", "New Name", "https://example.com/new.png");

        assertEquals(first.id(), second.id(), "same oidc_subject must resolve to the same account");
        assertEquals("new@example.com", second.email());
        assertEquals("New Name", second.displayName());
        assertEquals("https://example.com/new.png", second.avatarUrl());
    }

    @Test
    void differentSubjectsCreateDifferentAccountsEvenWithSameEmailHistory() {
        User a = users.upsertFromOidc("sub-a", "shared@example.com", "A", null);
        User b = users.upsertFromOidc("sub-b", "other@example.com", "B", null);
        assertTrue(!a.id().equals(b.id()));
    }

    @Test
    void searchExcludesCallerAndMatchesPrefixCaseInsensitively() {
        User alice = users.upsertFromOidc("sub-1", "alice@example.com", "Alice Smith", null);
        users.upsertFromOidc("sub-2", "bob@example.com", "Bob Jones", null);
        users.upsertFromOidc("sub-3", "carol@example.com", "Carol King", null);

        List<UserSummary> results = users.search(alice.id(), "b", 10);
        assertEquals(1, results.size());
        assertEquals("Bob Jones", results.get(0).displayName());
    }

    @Test
    void searchRespectsLimit() {
        for (int i = 0; i < 5; i++) {
            users.upsertFromOidc("sub-" + i, "match" + i + "@example.com", "Match " + i, null);
        }
        List<UserSummary> results = users.search("nonexistent-caller-id", "match", 2);
        assertEquals(2, results.size());
    }
}
