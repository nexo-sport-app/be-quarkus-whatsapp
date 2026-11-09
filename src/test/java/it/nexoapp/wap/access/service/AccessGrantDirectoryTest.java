package it.nexoapp.wap.access.service;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import it.nexoapp.common.security.AssociationSelectionRequiredException;
import it.nexoapp.common.tenant.TenantContext;
import it.nexoapp.common.tenant.TenantNotResolvedException;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whatsapp service authorizes callers only from its local {@code access_grant} projection (D-06, ROL-02),
 * proven with real Keycloak tokens and grants inserted by the test: no grant means no access, the association
 * header selects only among granted associations, and a data access without a tenant fails closed.
 */
@QuarkusTest
class AccessGrantDirectoryTest {

    /** Fixed subject of the realm user {@code president}. */
    private static final String PRESIDENT_SUBJECT = "00000000-0000-4000-8000-000000000001";

    /** First association granted to the president. */
    private static final UUID ASSOCIATION_A = UUID.fromString("a55d0000-0000-4000-8000-0000000000a1");

    /** Second association granted to the president. */
    private static final UUID ASSOCIATION_B = UUID.fromString("a55d0000-0000-4000-8000-0000000000b2");

    /* - keycloak - */
    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    /* - configuration - */
    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    String issuer;

    /* - persistence - */
    @Inject
    EntityManager entityManager;

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Removes the grants of the test subject so every test leaves the table as it found it.
     */
    @AfterEach
    void cleanGrants() {
        unscoped(() -> entityManager
                .createNativeQuery("DELETE FROM whatsapp.access_grant WHERE subject = :s")
                .setParameter("s", PRESIDENT_SUBJECT).executeUpdate());
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * A president with a local grant is authorized with the tenant of the grant, without any call to users.
     */
    @Test
    void grantedPresidentIsAuthorizedWithGrantedTenant() {
        insertGrant(ASSOCIATION_A, "PRESIDENT");

        given().auth().oauth2(presidentToken())
                .when().get("/probe-access")
                .then().statusCode(200)
                .body("associationId", equalTo(ASSOCIATION_A.toString()));
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * A token without any local grant is refused as not linked.
     */
    @Test
    void identityWithoutGrantIsNotLinked() {
        final String token = keycloak.getAccessToken("stranger", "password", "nexo-dev");

        given().auth().oauth2(token)
                .when().get("/probe-access")
                .then().statusCode(403)
                .body("code", equalTo("IDENTITY_NOT_LINKED"));
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * With grants in two associations the header selects one of them, and without the header the caller must choose.
     */
    @Test
    void headerSelectsOnlyAmongGrantedAssociations() {
        insertGrant(ASSOCIATION_A, "PRESIDENT");
        insertGrant(ASSOCIATION_B, "PRESIDENT");
        final String token = presidentToken();

        given().auth().oauth2(token)
                .header("x-association-id", ASSOCIATION_B.toString())
                .when().get("/probe-access")
                .then().statusCode(200)
                .body("associationId", equalTo(ASSOCIATION_B.toString()));

        given().auth().oauth2(token)
                .when().get("/probe-access")
                .then().statusCode(403)
                .body("code", equalTo(AssociationSelectionRequiredException.CODE));

        given().auth().oauth2(token)
                .header("x-association-id", UUID.randomUUID().toString())
                .when().get("/probe-access")
                .then().statusCode(403)
                .body("code", equalTo(AssociationSelectionRequiredException.CODE));
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * An unknown role name in the stored list is ignored and the known roles still authorize.
     */
    @Test
    void unknownRoleNameIsIgnored() {
        insertGrant(ASSOCIATION_A, "PRESIDENT,NOT_A_ROLE");

        given().auth().oauth2(presidentToken())
                .when().get("/probe-access")
                .then().statusCode(200)
                .body("associationId", equalTo(ASSOCIATION_A.toString()));
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * A grant without the required role does not authorize the endpoint.
     */
    @Test
    void grantWithoutRequiredRoleIsForbidden() {
        insertGrant(ASSOCIATION_A, "ATHLETE");

        given().auth().oauth2(presidentToken())
                .when().get("/probe-access")
                .then().statusCode(403);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * On a bare thread with a request context but no tenant scope, a data access fails with
     * {@link TenantNotResolvedException}: there is no default association.
     *
     * @throws Exception when the executor fails unexpectedly
     */
    @Test
    void dataAccessWithoutTenantFails() throws Exception {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            final ExecutionException failure = assertThrows(ExecutionException.class, () -> executor.submit(() -> {
                final ManagedContext requestContext = Arc.container().requestContext();
                requestContext.activate();
                try {
                    return QuarkusTransaction.requiringNew().call(() -> entityManager
                            .createQuery("select count(o) from OutboxEntity o", Long.class).getSingleResult());
                } finally {
                    requestContext.terminate();
                }
            }).get());

            assertTrue(hasCause(failure, TenantNotResolvedException.class),
                    "expected TenantNotResolvedException in the cause chain but was " + failure);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Gets a real access token of the realm user {@code president}.
     *
     * @return the access token
     */
    private String presidentToken() {
        return keycloak.getAccessToken("president", "password", "nexo-dev");
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Inserts a grant for the president with the Dev Services issuer.
     *
     * @param associationId the association granted
     * @param roles         comma-separated role names
     */
    private void insertGrant(final UUID associationId, final String roles) {
        unscoped(() -> entityManager.createNativeQuery("""
                        INSERT INTO whatsapp.access_grant
                            (issuer, subject, tenant_id, member_id, roles, access_version, created_by, updated_by)
                        VALUES (:i, :s, :t, gen_random_uuid(), :r, 1, 'test', 'test')
                        """)
                .setParameter("i", issuer)
                .setParameter("s", PRESIDENT_SUBJECT)
                .setParameter("t", associationId)
                .setParameter("r", roles)
                .executeUpdate());
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Runs a fixture statement unscoped inside its own new transaction.
     *
     * @param action the statement
     * @return the number of affected rows
     */
    private int unscoped(final java.util.function.IntSupplier action) {
        return TenantContext.callUnscoped(() -> QuarkusTransaction.requiringNew().call(action::getAsInt));
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Looks for a type in the cause chain.
     *
     * @param failure the failure
     * @param type    the type searched
     * @return true when the type is in the chain
     */
    private static boolean hasCause(final Throwable failure, final Class<? extends Throwable> type) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

}
