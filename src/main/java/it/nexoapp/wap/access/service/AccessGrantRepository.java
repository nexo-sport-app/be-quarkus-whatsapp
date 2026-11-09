package it.nexoapp.wap.access.service;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import it.nexoapp.wap.access.entity.AccessGrantEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

/**
 * Persistence of {@link AccessGrantEntity}. Package-private: only the access directory uses it. The table is global
 * (not a tenant entity), so its queries see the grants of every association.
 */
@ApplicationScoped
class AccessGrantRepository implements PanacheRepositoryBase<AccessGrantEntity, UUID> {

    /**
     * Finds the grants of one external identity.
     *
     * @param issuer  issuer of the token ({@code iss})
     * @param subject subject of the token ({@code sub})
     * @return the grants of the identity, one per association, empty when the identity has none
     */
    List<AccessGrantEntity> findByIssuerAndSubject(final String issuer, final String subject) {
        return list("issuer = ?1 and subject = ?2", issuer, subject);
    }

}
