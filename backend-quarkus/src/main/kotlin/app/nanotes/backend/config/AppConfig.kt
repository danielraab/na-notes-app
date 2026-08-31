package app.nanotes.backend.config

import io.quarkus.runtime.Startup
import jakarta.enterprise.context.ApplicationScoped
import java.util.Optional
import org.eclipse.microprofile.config.inject.ConfigProperty

/**
 * Runtime configuration loaded from environment variables. Every property
 * name here is shared, by name, with every other backend implementation in
 * this repository (see /README.md, ADR 0011) — do not rename without
 * updating .env.example and the root docs.
 *
 * `@Startup` forces eager construction so the required-variable check below
 * fails the process at boot, before any request is served, mirroring
 * backend-go's config.Load() called at the top of main().
 */
@Startup
@ApplicationScoped
class AppConfig(
    @ConfigProperty(name = "LISTEN_ADDR", defaultValue = ":8080") val listenAddr: String,
    @ConfigProperty(name = "PUBLIC_BASE_URL", defaultValue = "http://localhost:8080") val publicBaseUrl: String,
    @ConfigProperty(name = "FRONTEND_URL", defaultValue = "http://localhost:5173") val frontendUrl: String,
    @ConfigProperty(name = "ALLOWED_ORIGINS", defaultValue = "http://localhost:5173") val allowedOrigins: List<String>,
    @ConfigProperty(name = "SESSION_SECRET") sessionSecretRaw: Optional<String>,
    @ConfigProperty(name = "COOKIE_DOMAIN") cookieDomainRaw: Optional<String>,
    @ConfigProperty(name = "OIDC_ISSUER_URL") oidcIssuerUrlRaw: Optional<String>,
    @ConfigProperty(name = "OIDC_CLIENT_ID") oidcClientIdRaw: Optional<String>,
    @ConfigProperty(name = "OIDC_CLIENT_SECRET") oidcClientSecretRaw: Optional<String>,
    @ConfigProperty(name = "OIDC_REDIRECT_URL") oidcRedirectUrlRaw: Optional<String>,
    @ConfigProperty(name = "OIDC_SCOPES", defaultValue = "openid,profile,email") val oidcScopes: List<String>,
    @ConfigProperty(name = "DATABASE_URL", defaultValue = "./notes.db") val databaseUrl: String,
) {
    val sessionSecret: String = sessionSecretRaw.orElse("")
    val cookieDomain: String = cookieDomainRaw.orElse("")
    val oidcIssuerUrl: String = oidcIssuerUrlRaw.orElse("")
    val oidcClientId: String = oidcClientIdRaw.orElse("")
    val oidcClientSecret: String = oidcClientSecretRaw.orElse("")
    val oidcRedirectUrl: String = oidcRedirectUrlRaw.orElse("")

    init {
        val missing = buildList {
            if (sessionSecret.isEmpty()) add("SESSION_SECRET")
            if (oidcIssuerUrl.isEmpty()) add("OIDC_ISSUER_URL")
            if (oidcClientId.isEmpty()) add("OIDC_CLIENT_ID")
            if (oidcClientSecret.isEmpty()) add("OIDC_CLIENT_SECRET")
            if (oidcRedirectUrl.isEmpty()) add("OIDC_REDIRECT_URL")
        }
        check(missing.isEmpty()) { "missing required environment variables: ${missing.joinToString(", ")}" }
    }

    /** Cookies are only marked Secure once the backend is actually served over https. */
    fun secureCookies(): Boolean = publicBaseUrl.startsWith("https://")
}
