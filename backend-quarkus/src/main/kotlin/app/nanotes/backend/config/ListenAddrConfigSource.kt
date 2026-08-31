package app.nanotes.backend.config

import org.eclipse.microprofile.config.spi.ConfigSource

/**
 * Bridges the cross-implementation `LISTEN_ADDR` env var (a Go-style
 * ":8080" address, ADR 0011) onto Quarkus's own `quarkus.http.port`
 * property, which [AppConfig] can't do itself — HTTP port binding happens
 * before CDI beans exist. Registered via META-INF/services so SmallRye
 * Config picks it up automatically.
 */
class ListenAddrConfigSource : ConfigSource {

    companion object {
        private const val PROPERTY = "quarkus.http.port"

        private fun resolvePort(): String? {
            val addr = System.getenv("LISTEN_ADDR")
            if (addr.isNullOrBlank()) return null
            val idx = addr.lastIndexOf(':')
            if (idx < 0 || idx == addr.length - 1) return null
            return addr.substring(idx + 1).toIntOrNull()?.toString()
        }
    }

    override fun getProperties(): Map<String, String> {
        val port = resolvePort() ?: return emptyMap()
        return mapOf(PROPERTY to port)
    }

    override fun getPropertyNames(): Set<String> = getProperties().keys

    override fun getValue(propertyName: String): String? =
        if (PROPERTY == propertyName) resolvePort() else null

    override fun getName(): String = "listen-addr-bridge"

    // Above application.properties (250) so LISTEN_ADDR can override the
    // built-in default port, below a directly-set QUARKUS_HTTP_PORT env
    // var (300) or system property (400) so an explicit override wins.
    override fun getOrdinal(): Int = 275
}
