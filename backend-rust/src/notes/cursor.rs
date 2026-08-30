//! The stable sort key (`updated_at`, `id`) used to page through the notes
//! feed, per /docs/adr/0007-cursor-pagination.md. Opaque to clients; only
//! this module constructs or interprets it.

use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize)]
pub struct Cursor {
    #[serde(rename = "u")]
    pub updated_at: String,
    #[serde(rename = "i")]
    pub id: String,
}

pub fn encode_cursor(updated_at: &str, id: &str) -> String {
    let c = Cursor {
        updated_at: updated_at.to_string(),
        id: id.to_string(),
    };
    let bytes = serde_json::to_vec(&c).expect("cursor serializes");
    URL_SAFE_NO_PAD.encode(bytes)
}

pub fn decode_cursor(s: &str) -> Result<Cursor, String> {
    let bytes = URL_SAFE_NO_PAD
        .decode(s)
        .map_err(|e| format!("invalid cursor: {e}"))?;
    let c: Cursor = serde_json::from_slice(&bytes).map_err(|e| format!("invalid cursor: {e}"))?;
    if c.updated_at.is_empty() || c.id.is_empty() {
        return Err("invalid cursor".to_string());
    }
    Ok(c)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cursor_round_trip() {
        let updated_at = "2026-07-29T12:00:00Z";
        let encoded = encode_cursor(updated_at, "note-123");

        let got = decode_cursor(&encoded).expect("decode");
        assert_eq!(got.id, "note-123");
        assert_eq!(got.updated_at, updated_at);
    }

    #[test]
    fn decode_cursor_rejects_garbage() {
        assert!(decode_cursor("not-valid-base64!!").is_err());
        // base64("{}"), missing fields
        assert!(decode_cursor("e30").is_err());
    }
}
