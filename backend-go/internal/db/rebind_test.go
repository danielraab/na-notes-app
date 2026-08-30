package db

import "testing"

func TestRebindSQLiteIsNoop(t *testing.T) {
	q := `SELECT * FROM notes WHERE id = ? AND owner_id = ?`
	if got := rebind(driverSQLite, q); got != q {
		t.Errorf("rebind(sqlite, %q) = %q, want unchanged", q, got)
	}
}

func TestRebindPostgresNumbersPlaceholders(t *testing.T) {
	got := rebind(driverPostgres, `SELECT * FROM notes WHERE id = ? AND owner_id = ?`)
	want := `SELECT * FROM notes WHERE id = $1 AND owner_id = $2`
	if got != want {
		t.Errorf("rebind(postgres, ...) = %q, want %q", got, want)
	}
}

func TestRebindPostgresIgnoresPlaceholdersInsideStringLiterals(t *testing.T) {
	got := rebind(driverPostgres, `SELECT ? FROM t WHERE note = 'what?' AND id = ?`)
	want := `SELECT $1 FROM t WHERE note = 'what?' AND id = $2`
	if got != want {
		t.Errorf("rebind(postgres, ...) = %q, want %q", got, want)
	}
}

func TestSplitStatements(t *testing.T) {
	sql := `
CREATE TABLE a (id TEXT PRIMARY KEY);
CREATE TABLE b (
    id TEXT PRIMARY KEY,
    label TEXT NOT NULL CHECK (label IN ('x;y', 'z'))
);
CREATE INDEX idx_b ON b (label)
`
	stmts := splitStatements(sql)
	if len(stmts) != 3 {
		t.Fatalf("got %d statements, want 3: %#v", len(stmts), stmts)
	}
	if stmts[0] != "CREATE TABLE a (id TEXT PRIMARY KEY)" {
		t.Errorf("stmts[0] = %q", stmts[0])
	}
	if stmts[2] != "CREATE INDEX idx_b ON b (label)" {
		t.Errorf("stmts[2] = %q", stmts[2])
	}
}

func TestSplitStatementsEmptyInput(t *testing.T) {
	if got := splitStatements("  \n  "); len(got) != 0 {
		t.Errorf("got %#v, want no statements", got)
	}
}
