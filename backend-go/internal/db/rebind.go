package db

import "strconv"

// rebind rewrites the `?`-style positional placeholders used throughout
// this codebase's SQL (native to SQLite) into whatever placeholder syntax
// driver expects. SQLite is left untouched; PostgreSQL wants sequential
// `$1, $2, ...` placeholders instead. This lets every repository/store
// write a single set of queries without any awareness of which database
// engine is configured — see docs/decisions/0005-postgres-support-via-pgx.md.
func rebind(driver, query string) string {
	if driver != driverPostgres {
		return query
	}

	out := make([]byte, 0, len(query)+8)
	n := 0
	inString := false
	for i := 0; i < len(query); i++ {
		c := query[i]
		switch {
		case c == '\'':
			inString = !inString
			out = append(out, c)
		case c == '?' && !inString:
			n++
			out = append(out, '$')
			out = append(out, strconv.Itoa(n)...)
		default:
			out = append(out, c)
		}
	}
	return string(out)
}
