package it.nexoapp.wap.access.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import it.nexoapp.common.security.IdentityDirectory;
import it.nexoapp.common.security.IdentityKey;
import it.nexoapp.common.security.MembershipRef;
import it.nexoapp.common.security.Role;
import it.nexoapp.common.tenant.TenantContext;
import it.nexoapp.wap.access.entity.AccessGrantEntity;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * {@link IdentityDirectory} of the whatsapp service (D-06): resolves an external identity to its memberships
 * through the local {@code access_grant} projection, without calling the users service. The table is global, so it
 * is read unscoped inside its own transaction. An identity without grants resolves to nothing (fail closed).
 */
@ApplicationScoped
@RequiredArgsConstructor
class AccessGrantDirectory implements IdentityDirectory {

    /** Logger; it never receives issuer, subject or tenant values. */
    private static final Logger LOG = LoggerFactory.getLogger(AccessGrantDirectory.class);

    /* - repository - */
    private final AccessGrantRepository accessGrantRepository;

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * @param key {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    public List<MembershipRef> memberships(final IdentityKey key) {
        final List<AccessGrantEntity> grants = TenantContext.callUnscoped(() -> QuarkusTransaction.requiringNew()
                .call(() -> accessGrantRepository.findByIssuerAndSubject(key.issuer(), key.subject())));

        return grants.stream()
                .map(grant -> new MembershipRef(grant.getTenantId(), grant.getMemberId(), parseRoles(grant.getRoles())))
                .toList();
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Parses the comma-separated role names of a grant. Unknown names are ignored and logged without identifiers.
     *
     * @param roles the stored role list
     * @return the known roles, empty when none is recognised
     */
    private static Set<Role> parseRoles(final String roles) {
        final Set<Role> parsed = EnumSet.noneOf(Role.class);
        for (final String name : roles.split(",")) {
            final String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                parsed.add(Role.valueOf(trimmed));
            } catch (final IllegalArgumentException e) {
                LOG.warn("Ignoring an unknown role name in an access grant");
            }
        }
        return parsed;
    }

}
