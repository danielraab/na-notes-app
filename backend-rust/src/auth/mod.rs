//! Implements the OIDC authorization-code+PKCE login flow and the
//! server-side session it creates (ADR 0004).

mod oidc;
mod store;

// Session/OidcRequest are part of this module's public surface (mirroring
// backend-go's exported auth.Session/auth.OIDCRequest) even though only
// their fields, not their type names, are referenced outside this module.
#[allow(unused_imports)]
pub use oidc::{Claims, Oidc};
#[allow(unused_imports)]
pub use store::{OidcRequest, Session, Store};
