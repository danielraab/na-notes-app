//! Placeholder translation between the canonical SQL dialect repositories
//! write and what a given engine expects.

/// Rewrites the `?N` positional placeholders every repository writes (SQLite's
/// native syntax, which this codebase treats as canonical) into the `$N` form
/// PostgreSQL expects. Placeholders inside single-quoted string literals are
/// left alone.
///
/// Because both forms are *numbered* rather than sequential, this is a direct
/// 1:1 textual mapping — a query may reference the same parameter more than
/// once (as `users` search does with `?2`) and still rebind correctly, which a
/// counting rewrite of unnumbered `?` placeholders could not express.
pub fn to_dollar_placeholders(sql: &str) -> String {
    let mut out = String::with_capacity(sql.len());
    let mut in_string = false;
    let mut chars = sql.chars().peekable();

    while let Some(c) = chars.next() {
        match c {
            '\'' => {
                in_string = !in_string;
                out.push(c);
            }
            '?' if !in_string && chars.peek().is_some_and(char::is_ascii_digit) => {
                out.push('$');
                while let Some(d) = chars.peek().copied().filter(char::is_ascii_digit) {
                    out.push(d);
                    chars.next();
                }
            }
            _ => out.push(c),
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rewrites_numbered_placeholders() {
        assert_eq!(
            to_dollar_placeholders("SELECT a FROM t WHERE b = ?1 AND c = ?2"),
            "SELECT a FROM t WHERE b = $1 AND c = $2"
        );
    }

    #[test]
    fn rewrites_a_repeated_placeholder_to_the_same_number() {
        assert_eq!(
            to_dollar_placeholders("WHERE id != ?1 AND (name LIKE ?2 OR email LIKE ?2)"),
            "WHERE id != $1 AND (name LIKE $2 OR email LIKE $2)"
        );
    }

    #[test]
    fn handles_two_digit_placeholders() {
        assert_eq!(
            to_dollar_placeholders("VALUES (?9, ?10)"),
            "VALUES ($9, $10)"
        );
    }

    #[test]
    fn leaves_string_literals_alone() {
        assert_eq!(
            to_dollar_placeholders("SELECT '?1 literal' WHERE a = ?1"),
            "SELECT '?1 literal' WHERE a = $1"
        );
    }

    #[test]
    fn leaves_a_bare_question_mark_alone() {
        assert_eq!(to_dollar_placeholders("SELECT 'a?b'"), "SELECT 'a?b'");
    }
}
