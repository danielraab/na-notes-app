package app.nanotes.backend.web;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/** Anything not covered by a more specific mapper is an unexpected server error, logged with detail the client never sees. */
@Provider
public class UnhandledExceptionMapper implements ExceptionMapper<Throwable> {
    private static final Logger LOG = Logger.getLogger(UnhandledExceptionMapper.class);

    @Override
    public Response toResponse(Throwable e) {
        if (e instanceof WebApplicationException wae) {
            return wae.getResponse();
        }
        LOG.error("unhandled error", e);
        return ErrorResponses.of(Response.Status.INTERNAL_SERVER_ERROR, "INTERNAL", "internal server error");
    }
}
