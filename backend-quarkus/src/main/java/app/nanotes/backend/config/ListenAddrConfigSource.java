package app.nanotes.backend.config;

import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Bridges the cross-implementation {@code LISTEN_ADDR} env var (a Go-style
 * {@code ":8080"} address, ADR 0011) onto Quarkus's own
 * {@code quarkus.http.port} property, which {@code AppConfig} can't do
 * itself — HTTP port binding happens before CDI beans exist. Registered via
 * META-INF/services so SmallRye Config picks it up automatically.
 */
public class ListenAddrConfigSource implements ConfigSource {

    private static final String PROPERTY = "quarkus.http.port";

    @Override
    public Map<String, String> getProperties() {
        String port = resolvePort();
        return port == null ? Map.of() : Map.of(PROPERTY, port);
    }

    @Override
    public Set<String> getPropertyNames() {
        return getProperties().keySet();
    }

    @Override
    public String getValue(String propertyName) {
        return PROPERTY.equals(propertyName) ? resolvePort() : null;
    }

    @Override
    public String getName() {
        return "listen-addr-bridge";
    }

    @Override
    public int getOrdinal() {
        // Above application.properties (250) so LISTEN_ADDR can override the
        // built-in default port, below a directly-set QUARKUS_HTTP_PORT env
        // var (300) or system property (400) so an explicit override wins.
        return 275;
    }

    private static String resolvePort() {
        String addr = System.getenv("LISTEN_ADDR");
        if (addr == null || addr.isBlank()) {
            return null;
        }
        int idx = addr.lastIndexOf(':');
        if (idx < 0 || idx == addr.length() - 1) {
            return null;
        }
        String port = addr.substring(idx + 1);
        try {
            Integer.parseInt(port);
            return port;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
