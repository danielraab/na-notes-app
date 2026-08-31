package app.nanotes.backend.config;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Runtime configuration loaded from environment variables. Every property
 * name here is shared, by name, with every other backend implementation in
 * this repository (see /README.md, ADR 0011) — do not rename without
 * updating .env.example and the root docs.
 *
 * {@code @Startup} forces eager construction so the required-variable check
 * below fails the process at boot, before any request is served, mirroring
 * backend-go's config.Load() called at the top of main().
 */
@Startup
@ApplicationScoped
public class AppConfig {

    private final String listenAddr;
    private final String publicBaseUrl;
    private final String frontendUrl;
    private final List<String> allowedOrigins;

    private final String sessionSecret;
    private final String cookieDomain;

    private final String oidcIssuerUrl;
    private final String oidcClientId;
    private final String oidcClientSecret;
    private final String oidcRedirectUrl;
    private final List<String> oidcScopes;

    private final String databaseUrl;

    @SuppressWarnings("java:S107") // one field per env var, mirrors config.go's flat struct
    public AppConfig(
            @ConfigProperty(name = "LISTEN_ADDR", defaultValue = ":8080") String listenAddr,
            @ConfigProperty(name = "PUBLIC_BASE_URL", defaultValue = "http://localhost:8080") String publicBaseUrl,
            @ConfigProperty(name = "FRONTEND_URL", defaultValue = "http://localhost:5173") String frontendUrl,
            @ConfigProperty(name = "ALLOWED_ORIGINS", defaultValue = "http://localhost:5173") List<String> allowedOrigins,
            @ConfigProperty(name = "SESSION_SECRET") Optional<String> sessionSecret,
            @ConfigProperty(name = "COOKIE_DOMAIN") Optional<String> cookieDomain,
            @ConfigProperty(name = "OIDC_ISSUER_URL") Optional<String> oidcIssuerUrl,
            @ConfigProperty(name = "OIDC_CLIENT_ID") Optional<String> oidcClientId,
            @ConfigProperty(name = "OIDC_CLIENT_SECRET") Optional<String> oidcClientSecret,
            @ConfigProperty(name = "OIDC_REDIRECT_URL") Optional<String> oidcRedirectUrl,
            @ConfigProperty(name = "OIDC_SCOPES", defaultValue = "openid,profile,email") List<String> oidcScopes,
            @ConfigProperty(name = "DATABASE_URL", defaultValue = "./notes.db") String databaseUrl) {
        this.listenAddr = listenAddr;
        this.publicBaseUrl = publicBaseUrl;
        this.frontendUrl = frontendUrl;
        this.allowedOrigins = allowedOrigins;
        this.sessionSecret = sessionSecret.orElse("");
        this.cookieDomain = cookieDomain.orElse("");
        this.oidcIssuerUrl = oidcIssuerUrl.orElse("");
        this.oidcClientId = oidcClientId.orElse("");
        this.oidcClientSecret = oidcClientSecret.orElse("");
        this.oidcRedirectUrl = oidcRedirectUrl.orElse("");
        this.oidcScopes = oidcScopes;
        this.databaseUrl = databaseUrl;

        List<String> missing = new java.util.ArrayList<>();
        if (this.sessionSecret.isEmpty()) missing.add("SESSION_SECRET");
        if (this.oidcIssuerUrl.isEmpty()) missing.add("OIDC_ISSUER_URL");
        if (this.oidcClientId.isEmpty()) missing.add("OIDC_CLIENT_ID");
        if (this.oidcClientSecret.isEmpty()) missing.add("OIDC_CLIENT_SECRET");
        if (this.oidcRedirectUrl.isEmpty()) missing.add("OIDC_REDIRECT_URL");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("missing required environment variables: " + String.join(", ", missing));
        }
    }

    public String listenAddr() {
        return listenAddr;
    }

    public String publicBaseUrl() {
        return publicBaseUrl;
    }

    public String frontendUrl() {
        return frontendUrl;
    }

    public List<String> allowedOrigins() {
        return allowedOrigins;
    }

    public String sessionSecret() {
        return sessionSecret;
    }

    public String cookieDomain() {
        return cookieDomain;
    }

    public String oidcIssuerUrl() {
        return oidcIssuerUrl;
    }

    public String oidcClientId() {
        return oidcClientId;
    }

    public String oidcClientSecret() {
        return oidcClientSecret;
    }

    public String oidcRedirectUrl() {
        return oidcRedirectUrl;
    }

    public List<String> oidcScopes() {
        return oidcScopes;
    }

    public String databaseUrl() {
        return databaseUrl;
    }

    /** Cookies are only marked Secure once the backend is actually served over https. */
    public boolean secureCookies() {
        return publicBaseUrl.startsWith("https://");
    }
}
