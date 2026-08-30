package db

import "strconv"

// escapeSQLiteURIPath percent-encodes the characters that are significant
// to SQLite/modernc's "file:" URI DSN parsing — '%' itself, '#' (which
// otherwise truncates the path at a URI fragment, silently opening a
// different, shorter path than intended), and '?' (which would otherwise
// start the query string early, splitting the path from the pragmas that
// follow it). '%' must be escaped first so the '#'/'?' escapes it
// introduces aren't themselves re-escaped.
func escapeSQLiteURIPath(path string) string {
	out := make([]byte, 0, len(path))
	for i := 0; i < len(path); i++ {
		switch c := path[i]; c {
		case '%':
			out = append(out, "%25"...)
		case '#':
			out = append(out, "%23"...)
		case '?':
			out = append(out, "%3F"...)
		default:
			out = append(out, c)
		}
	}
	return string(out)
}

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
