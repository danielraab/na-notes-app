package app.nanotes.backend.auth

import app.nanotes.backend.config.AppConfig
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import io.quarkus.runtime.Startup
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64

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
class OidcClient private constructor(
    private val clientId: String,
    private val clientSecret: String,
    private val redirectUrl: String,
    private val scopes: List<String>,
    issuerUrl: String,
) {
    @Inject
    constructor(config: AppConfig) : this(
        config.oidcClientId,
        config.oidcClientSecret,
        config.oidcRedirectUrl,
        config.oidcScopes,
        config.oidcIssuerUrl,
    )

    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build()
    private val json = ObjectMapper()

    private val metadata: JsonNode = discover(issuerUrl)
    private val authorizationEndpoint: String = requireText(metadata, "authorization_endpoint", issuerUrl)
    private val tokenEndpoint: String = requireText(metadata, "token_endpoint", issuerUrl)
    private val issuer: String = requireText(metadata, "issuer", issuerUrl)
    private val jwksUri: String = requireText(metadata, "jwks_uri", issuerUrl)

    private val jwtProcessor: ConfigurableJWTProcessor<SecurityContext> = try {
        val jwkSource: JWKSource<SecurityContext> = JWKSourceBuilder.create<SecurityContext>(URI.create(jwksUri).toURL()).build()
        val processor = DefaultJWTProcessor<SecurityContext>()
        processor.setJWSKeySelector(JWSVerificationKeySelector(ACCEPTED_ALGORITHMS, jwkSource))
        val exactMatch = JWTClaimsSet.Builder().issuer(issuer).audience(clientId).build()
        processor.setJWTClaimsSetVerifier(DefaultJWTClaimsVerifier(exactMatch, setOf("sub", "iss", "aud", "exp")))
        processor
    } catch (e: Exception) {
        throw OidcException("configure id_token verifier for $issuerUrl", e)
    }

    private fun discover(issuerUrl: String): JsonNode {
        val url = issuerUrl.trimEnd('/') + "/.well-known/openid-configuration"
        return try {
            val req = HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT).GET().build()
            val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() >= 300) {
                throw OidcException("discover OIDC provider $issuerUrl: HTTP ${resp.statusCode()}")
            }
            json.readTree(resp.body())
        } catch (e: OidcException) {
            throw e
        } catch (e: Exception) {
            throw OidcException("discover OIDC provider $issuerUrl", e)
        }
    }

    private fun requireText(metadata: JsonNode, field: String, issuerUrl: String): String {
        val v = metadata.get(field)
        if (v == null || v.isNull) {
            throw OidcException("OIDC provider metadata for $issuerUrl is missing \"$field\"")
        }
        return v.asText()
    }

    fun authCodeUrl(state: String, codeVerifier: String): String {
        val scopeParam = scopes.joinToString(" ")
        return authorizationEndpoint + "?" +
            "response_type=code" +
            "&client_id=${enc(clientId)}" +
            "&redirect_uri=${enc(redirectUrl)}" +
            "&scope=${enc(scopeParam)}" +
            "&state=${enc(state)}" +
            "&code_challenge=${enc(codeChallenge(codeVerifier))}" +
            "&code_challenge_method=S256"
    }

    /** Completes the authorization code flow and verifies the returned ID token, returning the caller's identity claims. */
    fun exchange(code: String, codeVerifier: String): Claims {
        val form = "grant_type=authorization_code" +
            "&code=${enc(code)}" +
            "&redirect_uri=${enc(redirectUrl)}" +
            "&client_id=${enc(clientId)}" +
            "&client_secret=${enc(clientSecret)}" +
            "&code_verifier=${enc(codeVerifier)}"

        val tokenResponse: JsonNode = try {
            val req = HttpRequest.newBuilder(URI.create(tokenEndpoint))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build()
            val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() >= 300) {
                throw OidcException("exchange authorization code: HTTP ${resp.statusCode()}")
            }
            json.readTree(resp.body())
        } catch (e: OidcException) {
            throw e
        } catch (e: Exception) {
            throw OidcException("exchange authorization code", e)
        }

        val idTokenNode = tokenResponse.get("id_token")
        if (idTokenNode == null || idTokenNode.isNull) {
            throw OidcException("token response did not include an id_token")
        }

        val claims: JWTClaimsSet = try {
            jwtProcessor.process(idTokenNode.asText(), null)
        } catch (e: Exception) {
            throw OidcException("verify id_token", e)
        }

        return try {
            val email = stringClaim(claims, "email") ?: ""
            val name = stringClaim(claims, "name")
            val picture = stringClaim(claims, "picture")
            val displayName = if (name.isNullOrEmpty()) email else name
            Claims(claims.subject, email, displayName, picture)
        } catch (e: java.text.ParseException) {
            throw OidcException("parse id_token claims", e)
        }
    }

    private fun stringClaim(claims: JWTClaimsSet, name: String): String? {
        claims.getClaim(name) ?: return null
        return claims.getStringClaim(name)
    }

    companion object {
        private val HTTP_TIMEOUT: Duration = Duration.ofSeconds(30)
        private val ACCEPTED_ALGORITHMS: Set<JWSAlgorithm> = setOf(
            JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
            JWSAlgorithm.PS256, JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512,
        )

        /** Derives the PKCE S256 code_challenge for a code_verifier. */
        fun codeChallenge(codeVerifier: String): String {
            val sum = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(StandardCharsets.UTF_8))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sum)
        }

        private fun enc(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)
    }
}
