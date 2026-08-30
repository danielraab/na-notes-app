//! RFC 3339 (nanosecond) timestamp formatting shared by every module that
//! stores a `chrono::DateTime<Utc>` as SQLite `TEXT`, mirroring
//! backend-go's `time.RFC3339Nano` convention so on-disk timestamps stay
//! human-readable and sortable as text.

use chrono::{DateTime, Utc};

pub fn fmt_time(t: DateTime<Utc>) -> String {
    t.to_rfc3339_opts(chrono::SecondsFormat::AutoSi, true)
}

pub fn parse_time(s: &str) -> DateTime<Utc> {
    DateTime::parse_from_rfc3339(s)
        .map(|dt| dt.with_timezone(&Utc))
        .unwrap_or_default()
}
