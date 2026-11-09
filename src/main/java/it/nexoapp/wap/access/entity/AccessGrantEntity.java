package it.nexoapp.wap.access.entity;

import it.nexoapp.common.hibernate.entity.AuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * Local projection of the access an external identity holds in one association (D-06). The users service owns the
 * truth; this service keeps its own copy so it can authorize a caller without calling users. It is a global table,
 * not a tenant entity: it is read before the association of the caller is known, so it is read only under
 * {@code TenantContext.callUnscoped}. The tenant identifier is a plain data column.
 * <p>
 * In phase 1 the rows come only from test fixtures; the {@code identity.access.changed} feed fills it later (ROL-13).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "access_grant",
        uniqueConstraints = @UniqueConstraint(
                name = "access_grant_uq",
                columnNames = {"issuer", "subject", "tenant_id"}
        )
)
public class AccessGrantEntity extends AuditEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 6204118835719402261L;

    /* identifiers */
    /** Unique identifier of the grant. */
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(
            name = "id",
            columnDefinition = "UUID",
            nullable = false,
            unique = true
    )
    private UUID id;

    /* external identity */
    /** Issuer of the token ({@code iss} claim). */
    @Column(
            name = "issuer",
            columnDefinition = "TEXT",
            nullable = false
    )
    private String issuer;

    /** Subject of the token ({@code sub} claim), unique within the issuer. */
    @Column(
            name = "subject",
            columnDefinition = "TEXT",
            nullable = false
    )
    private String subject;

    /* granted access */
    /** Identifier of the association the grant applies to (plain data, not a tenant filter). */
    @Column(
            name = "tenant_id",
            columnDefinition = "UUID",
            nullable = false,
            updatable = false
    )
    private UUID tenantId;

    /** Identifier of the member record of the identity inside the association. */
    @Column(
            name = "member_id",
            columnDefinition = "UUID",
            nullable = false
    )
    private UUID memberId;

    /** Roles held in the association, as a comma-separated list of role names. */
    @Column(
            name = "roles",
            columnDefinition = "TEXT",
            nullable = false
    )
    private String roles;

    /** Monotonic version of the access in the users service, used to order feed updates (ROL-13). */
    @Column(
            name = "access_version",
            columnDefinition = "BIGINT",
            nullable = false
    )
    private long accessVersion;

}
