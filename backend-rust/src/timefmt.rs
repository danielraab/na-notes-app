//! RFC 3339 (nanosecond) timestamp formatting shared by every module that
//! stores a `chrono::DateTime<Utc>` as a `TEXT` column, mirroring
//! backend-go's `time.RFC3339Nano` convention so stored timestamps stay
//! human-readable, sortable as text on either engine, and comparable across
//! implementations (/docs/schema.md).

use chrono::{DateTime, Utc};

pub fn fmt_time(t: DateTime<Utc>) -> String {
    t.to_rfc3339_opts(chrono::SecondsFormat::AutoSi, true)
}

pub fn parse_time(s: &str) -> DateTime<Utc> {
    DateTime::parse_from_rfc3339(s)
        .map(|dt| dt.with_timezone(&Utc))
        .unwrap_or_default()
}
