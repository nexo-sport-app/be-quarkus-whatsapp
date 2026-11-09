# be-quarkus-whatsapp

WhatsApp microservice of Nexo: it will hold the WhatsApp delivery channel (domain arrives in Phase 7). Today it is the platform
skeleton: tenant-safe persistence, outbox and inbox, health, and local authorization. It is a scripted clone of the
`be-quarkus-documents` skeleton template with only the service identifiers renamed.

## What is in it

- Root path `/nexo-wap`, port `8087` in dev.
- Own Postgres schema `whatsapp`, own database role `svc_wap`, own Flyway history.
- `outbox` and `inbox` tables (copied from the `be-quarkus-messaging` README).
- `access_grant`: global local projection of "which identity holds which roles in which association". It is read
  unscoped by `AccessGrantDirectory` (the `IdentityDirectory` of this service), so the service authorizes callers
  without calling the users service (D-06). An identity without a grant gets `403 IDENTITY_NOT_LINKED`; with several
  grants the `x-association-id` header must pick one of them. Unknown role names in a grant are ignored and logged.
- In Phase 1 the table is filled only by test fixtures. The `identity.access.changed` feed from users fills it in
  Phase 2 (ROL-13), ordered by `access_version`.

## Run in dev

Start the compose stack from `nexo-platform` (Postgres on host port 55432, RabbitMQ, Keycloak on 9090), then:

```
mvn quarkus:dev
```

Health: `http://localhost:8087/q/health`.

## Test

```
mvn verify -Dquarkus.http.test-port=18088
```

Tests use Quarkus Dev Services (Postgres, RabbitMQ, Keycloak with the `nexo` realm); Docker must be running.
The port override avoids clashes when several services are tested at the same time.
