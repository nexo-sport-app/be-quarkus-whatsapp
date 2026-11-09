CREATE TABLE access_grant (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    issuer          TEXT NOT NULL,
    subject         TEXT NOT NULL,
    tenant_id       UUID NOT NULL,
    member_id       UUID NOT NULL,
    roles           TEXT NOT NULL,
    access_version  BIGINT NOT NULL,
    created_by      TEXT NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT now() NOT NULL,
    updated_by      TEXT NOT NULL,
    updated_at      TIMESTAMPTZ DEFAULT now() NOT NULL,
    CONSTRAINT access_grant_pk PRIMARY KEY (id),
    CONSTRAINT access_grant_uq UNIQUE (issuer, subject, tenant_id)
);
CREATE INDEX idx_access_grant_issuer_subject ON access_grant (issuer, subject);
