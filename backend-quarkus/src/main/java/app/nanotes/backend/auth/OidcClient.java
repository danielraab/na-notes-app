package app.nanotes.backend.auth;

import app.nanotes.backend.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wraps a standards-compliant OpenID Connect provider (authorization code +
 * PKCE, ADR 0004). Intentionally provider-agnostic: configuration is
 * limited to the issuer URL and client credentials, nothing provider
 * specific. Discovery + JWKS fetching use plain HTTP (java.net.http) and
 * Jackson; ID token signature verification uses nimbus-jose-jwt — see
 * docs/decisions/0002-manual-oidc-client.md for why this isn't the
 * quarkus-oidc extension.
 */
@Startup
@ApplicationScoped
public class OidcClient {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final Set<JWSAlgorithm> ACCEPTED_ALGORITHMS =
            Set.of(JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512, JWSAlgorithm.PS256, JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
    private final ObjectMapper json = new ObjectMapper();

    private final String clientId;
    private final String clientSecret;
    private final String redirectUrl;
    private final List<String> scopes;

    private final String authorizationEndpoint;
    private final String tokenEndpoint;
    private final String issuer;
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    @Inject
    public OidcClient(AppConfig config) {
        this(config.oidcIssuerUrl(), config.oidcClientId(), config.oidcClientSecret(), config.oidcRedirectUrl(), config.oidcScopes());
    }

    OidcClient(String issuerUrl, String clientId, String clientSecret, String redirectUrl, List<String> scopes) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUrl = redirectUrl;
        this.scopes = scopes;

        JsonNode metadata = discover(issuerUrl);
        this.authorizationEndpoint = requireText(metadata, "authorization_endpoint", issuerUrl);
        this.tokenEndpoint = requireText(metadata, "token_endpoint", issuerUrl);
        this.issuer = requireText(metadata, "issuer", issuerUrl);
        String jwksUri = requireText(metadata, "jwks_uri", issuerUrl);

        try {
            JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(URI.create(jwksUri).toURL());
            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(ACCEPTED_ALGORITHMS, jwkSource));
            JWTClaimsSet exactMatch = new JWTClaimsSet.Builder().issuer(issuer).audience(clientId).build();
            processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(exactMatch, Set.of("sub", "iss", "aud", "exp")));
            this.jwtProcessor = processor;
        } catch (Exception e) {
            throw new OidcException("configure id_token verifier for " + issuerUrl, e);
        }
    }

    private JsonNode discover(String issuerUrl) {
        String url = issuerUrl.replaceAll("/+$", "") + "/.well-known/openid-configuration";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new OidcException("discover OIDC provider " + issuerUrl + ": HTTP " + resp.statusCode());
            }
            return json.readTree(resp.body());
        } catch (OidcException e) {
            throw e;
        } catch (Exception e) {
            throw new OidcException("discover OIDC provider " + issuerUrl, e);
        }
    }

    private static String requireText(JsonNode metadata, String field, String issuerUrl) {
        JsonNode v = metadata.get(field);
        if (v == null || v.isNull()) {
            throw new OidcException("OIDC provider metadata for " + issuerUrl + " is missing \"" + field + "\"");
        }
        return v.asText();
    }

    /** Derives the PKCE S256 code_challenge for a code_verifier. */
    public static String codeChallenge(String codeVerifier) {
        try {
            byte[] sum = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sum);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String authCodeUrl(String state, String codeVerifier) {
        String scopeParam = scopes.stream().collect(Collectors.joining(" "));
        return authorizationEndpoint + "?"
                + "response_type=code"
                + "&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUrl)
                + "&scope=" + enc(scopeParam)
                + "&state=" + enc(state)
                + "&code_challenge=" + enc(codeChallenge(codeVerifier))
                + "&code_challenge_method=S256";
    }

    /** Completes the authorization code flow and verifies the returned ID token, returning the caller's identity claims. */
    public Claims exchange(String code, String codeVerifier) {
        String form = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(redirectUrl)
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&code_verifier=" + enc(codeVerifier);

        JsonNode tokenResponse;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(tokenEndpoint))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new OidcException("exchange authorization code: HTTP " + resp.statusCode());
            }
            tokenResponse = json.readTree(resp.body());
        } catch (OidcException e) {
            throw e;
        } catch (Exception e) {
            throw new OidcException("exchange authorization code", e);
        }

        JsonNode idTokenNode = tokenResponse.get("id_token");
        if (idTokenNode == null || idTokenNode.isNull()) {
            throw new OidcException("token response did not include an id_token");
        }

        JWTClaimsSet claims;
        try {
            claims = jwtProcessor.process(idTokenNode.asText(), null);
        } catch (Exception e) {
            throw new OidcException("verify id_token", e);
        }

        try {
            String email = stringClaim(claims, "email");
            String name = stringClaim(claims, "name");
            String picture = stringClaim(claims, "picture");
            String displayName = (name == null || name.isEmpty()) ? email : name;
            return new Claims(claims.getSubject(), email, displayName, picture);
        } catch (java.text.ParseException e) {
            throw new OidcException("parse id_token claims", e);
        }
    }

    private static String stringClaim(JWTClaimsSet claims, String name) throws java.text.ParseException {
        Object v = claims.getClaim(name);
        return v == null ? "" : claims.getStringClaim(name);
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
