package it.nexoapp.wap;

import it.nexoapp.common.context.RequestContext;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.UUID;

/**
 * Test-only endpoint (lives in test sources, never ships): shows which association the identity gate resolved for
 * the caller. It is reachable only with the {@code PRESIDENT} role of the resolved association.
 */
@Path("/probe-access")
public class ProbeAccessResource {

    /* - context - */
    @Inject
    RequestContext requestContext;

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Returns the association resolved for the caller.
     *
     * @return the association identifier
     */
    @GET
    @RolesAllowed("PRESIDENT")
    @Produces(MediaType.APPLICATION_JSON)
    public ProbeResponse associationId() {
        return new ProbeResponse(requestContext.getAssociationId());
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Response of the probe.
     *
     * @param associationId the association resolved for the caller
     */
    public record ProbeResponse(UUID associationId) {
    }

}
