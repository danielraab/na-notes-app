//! The engine-neutral parameter and result types every repository speaks.
//!
//! Deliberately small: this application's schema only ever stores text and
//! integers (see /docs/schema.md), so a three-variant value covers every
//! bind parameter and every column read, and each backend only has to know
//! how to convert *these* to and from its own driver's types.

use crate::apperr::{AppError, Result};

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Value {
    Null,
    Text(String),
    Int(i64),
}

impl From<String> for Value {
    fn from(v: String) -> Value {
        Value::Text(v)
    }
}

impl From<&String> for Value {
    fn from(v: &String) -> Value {
        Value::Text(v.clone())
    }
}

impl From<&str> for Value {
    fn from(v: &str) -> Value {
        Value::Text(v.to_string())
    }
}

impl From<i64> for Value {
    fn from(v: i64) -> Value {
        Value::Int(v)
    }
}

impl From<Option<String>> for Value {
    fn from(v: Option<String>) -> Value {
        match v {
            Some(s) => Value::Text(s),
            None => Value::Null,
        }
    }
}

impl From<&Option<String>> for Value {
    fn from(v: &Option<String>) -> Value {
        Value::from(v.clone())
    }
}

/// Builds the `Vec<Value>` parameter list for a query, positionally
/// matching the `?1, ?2, ...` placeholders in its SQL.
#[macro_export]
macro_rules! params {
    () => { ::std::vec::Vec::<$crate::db::Value>::new() };
    ($($value:expr),+ $(,)?) => {
        ::std::vec![$($crate::db::Value::from($value)),+]
    };
}

/// One result row, as a positional list of columns in `SELECT` order.
#[derive(Debug, Clone)]
pub struct Row {
    values: Vec<Value>,
}

impl Row {
    pub fn new(values: Vec<Value>) -> Row {
        Row { values }
    }

    fn at(&self, idx: usize) -> Result<&Value> {
        self.values
            .get(idx)
            .ok_or_else(|| AppError::Internal(format!("column {idx} out of range")))
    }

    /// Reads a non-null text column.
    pub fn text(&self, idx: usize) -> Result<String> {
        match self.at(idx)? {
            Value::Text(s) => Ok(s.clone()),
            other => Err(AppError::Internal(format!(
                "column {idx}: expected text, got {other:?}"
            ))),
        }
    }

    /// Reads a nullable text column.
    pub fn opt_text(&self, idx: usize) -> Result<Option<String>> {
        match self.at(idx)? {
            Value::Null => Ok(None),
            Value::Text(s) => Ok(Some(s.clone())),
            other => Err(AppError::Internal(format!(
                "column {idx}: expected text or null, got {other:?}"
            ))),
        }
    }

    /// Reads a non-null integer column.
    pub fn int(&self, idx: usize) -> Result<i64> {
        match self.at(idx)? {
            Value::Int(i) => Ok(*i),
            other => Err(AppError::Internal(format!(
                "column {idx}: expected integer, got {other:?}"
            ))),
        }
    }
}
