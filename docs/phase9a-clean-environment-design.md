# Phase 9A — independent clean SaaS environment and startup design

Option A selected by the project owner. Prepared3 November2026. This document specifies future implementation/approval, not deployed infrastructure. Existing FitCore production resources, routes, customer accounts and credentials remain outside scope. Current startup prohibition stays unchanged.

## Independent resource boundary

| Resource | Proposed new SaaS resource | Required separation |
| --- | --- | --- |
| PostgreSQL | New database/instance in owner-approved region/plan | No old DB endpoint, import, shared schema, owner role or runtime credentials; network restrict to approved new services/operator |
| Database identities | Non-login schema owner, separately controlled migration login, restricted application runtime login | Runtime owns no objects, cannot inherit owner/migrator, no DDL/TRUNCATE/helper/history mutation; explicit26-table DML, sequence USAGE/SELECT and history SELECT; reviewed PUBLIC/default ACLs |
| Render backend | New service/project/environment for immutable SaaS artifact | New secret group/runtime identity, no reused old Render env groups/DB links/deploy hooks; initially auto-deploy held; exact reviewed service identifiers |
| Vercel frontend | New project with independent origin and API base | No edit/reuse of old project/environment/deployment; separate browser storage/cookie namespace, exact CORS; no old JWT accepted |
| Identity/secrets | Separate JWT signing material, DB/provider credentials and outbox keyring | Secret manager/access/audit/rotation; no copied production .env or inherited shell config; no secrets in Vite variables/browser bundle |
| Backup/recovery | New encrypted backup policy/storage, independently recoverable keys and compatible artifacts | Signed actual new-target restore certification, ACL/sequence reconciliation, measured RPO/RTO; no legacy backup access needed |
| Monitoring/release | Independent dashboards/alerts/audit, CI artifacts, manual deployment and rollback approvals | No old-system routing changes; rollback preserves new SaaS data and cannot redirect users to old FitCore as a substitute |

Sizing, region/data residency, availability, exact product identifiers, price/quotas and maintenance plan are unresolved owner decisions. No cloud provider API/CLI, credentials, resource provisioning or DNS action is part of this phase. Hosted private beta with real users is production, not this disposable demo.

## Configuration inventory — names only

Existing environment names consumed by application/build: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MS`, `CORS_ALLOWED_ORIGINS`, `PORT`, `JPA_SHOW_SQL`, `JPA_FORMAT_SQL`, `SQL_INIT_MODE`, `AWS_S3_ENABLED`, `VITE_API_BASE_URL`.

Exact Spring property names below are configuration keys, not an assertion that similarly spelled underscore aliases exist. Validate bindings through the eventual deployment tooling; never guess environment aliases for hyphenated properties:

- `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password`, `spring.datasource.hikari.schema`
- `spring.jpa.hibernate.ddl-auto`, `spring.sql.init.mode`, `spring.flyway.enabled`, `spring.flyway.baseline-on-migrate`, `spring.flyway.clean-disabled`
- `jwt.secret`, `jwt.expiration-ms`, `jwt.allowed-issue-clock-skew-seconds`, `cors.allowed-origins`, `server.forward-headers-strategy`
- `fitcore.onboarding.demo-enabled`, `fitcore.security-flows.demo-enabled`, `fitcore.security.public-origin`
- `fitcore.email.provider`, `fitcore.email.outbox.enabled`, `fitcore.email.outbox.worker-enabled`, `fitcore.email.outbox.active-key`, `fitcore.email.outbox.keys`, `aws.s3.enabled`

Test-only harness names: `FITCORE_TENANCY_TEST_TOKEN`, `FITCORE_POSTGRES_TEST_URL`, `FITCORE_POSTGRES_TEST_USERNAME`, `FITCORE_POSTGRES_TEST_PASSWORD`, `FITCORE_POSTGRES_TEST_IMAGE`, `FITCORE_VERIFY_OWNER_UI`, `FITCORE_VERIFY_PHASE3_UI`, `FITCORE_VERIFY_SECURITY_UI`. Never configure these for production or spoof a disposable marker. S3 credentials are not needed because assets remain disabled. Provider credentials/webhook secret, backup/KMS locations, monitoring tokens and future certified-target configuration names remain UNDEFINED until their adapters/designs are approved; no fictitious working variables are provided.

The old-system default CORS origins must be explicitly replaced by approved exact new HTTPS origins. New frontend API base must never resolve to old backend. Build/config tests should reject old resource identifiers without connecting to them; operator supplies approved new identities through protected scope.

## Future fail-closed startup mechanism — design only

No new production-enabling property or executable guard is added. Current guard must continue rejecting persistent databases and alternate selectors during all local tests.

A later separately reviewed release can distinguish TEST_DISPOSABLE from CERTIFIED_CLEAN_SAAS in a typed startup policy. The latter is not enabled by a boolean or a database comment. Proposed gates:

1. **Before connection:** require owner-approved immutable artifact/environment manifest, independently verified release authorization/expiry and new service identity. Parse exactly one approved canonical PostgreSQL/TLS target (host/port/database/schema); reject alternative Hikari/Flyway/JNDI/property selectors, URL query-based target overrides, service-file/JVM/env indirection, unsafe connection imports and all known old identifiers. Verify effective relaxed bindings, not just one environment key. No guessed old credentials are read.
2. **Target authentication:** connect only using separately approved new runtime secret and certificate/hostname-verified TLS plus network controls. Verify current database/schema/user, expected ownership/effective grants, engine and certified database identity. OID/database name/DNS/comment alone are insufficient, especially after restore. Use a controlled provision-time identity attestation bound to the service/project/target certificate and manifest, plus DB metadata checks. Exact attestation schema/signature/custody and restore identity rotation require approval; no new table is created here.
3. **Schema certification:** Flyway migrate runs as a separately approved job, never app startup. Require successful history/checksums and reviewed schema-security fingerprint for clean path. Do not insert fakeV3. Reject nonempty unknown schemas and legacy mapping artifacts. Runtime keeps Hibernate validate and SQL init never. Future legacyV3 packaging/history must not invalidate clean installations or be silently ignored.
4. **Runtime privilege certification:** enforce actual effective role/default ACL/function grants, not just login flags. Migration credentials never enter application runtime; roles/ACLs must be restored and reverified after recovery. Readiness remains fail-closed on drift.
5. **Operational certification:** match approved backup/restore/RPO/RTO receipt, security decision record and explicit deployment authorization to target/artifact/config version. Expired/missing approvals fail closed. Receipts are verified in a protected release control process; arbitrary database rows/strings/flags are not human approval. Define which evidence must remain valid at startup and which dependencies are monitored continuously.
6. **Activation:** exact matched backend/frontend artifacts and approved CORS/session boundary. Public verification/onboarding, provider/worker/proxy modes need separately reviewed production configurations, not reuse of demo flags. Readiness must understand certified modes while retaining negative tests; it cannot merely always return READY or omit incompatible checks.

Required future tests: old/unknown/mismatched target rejected before connection; every alternate binding/connection route rejected; forged/expired/missing certification refused; wrong runtime role, mutated migration history/constraint/trigger/sequence privilege rejected; valid certified synthetic target passes in an isolated policy test; restore identity/schema/key mismatch refuses service. Keep the existing startup guard until this implementation/security review and operational authority are separately approved.

## Ordered later deployment approvals

1. Approve distinct resources/region/budget, fresh accounts/no import and operators; approve catalog provisioning scope and first-owner recovery.
2. Approve production startup/readiness, browser transport, proxy/abuse, keys/secrets, provider/webhook/dedup horizon and worker/cleanup implementation designs.
3. Complete synthetic implementation/security tests and exact-artifact hosted CI plus required branch checks; keep automatic deploys held.
4. Separately approve new resource provisioning, certificates/domains/secret injection and empty target verification; never alter existing FitCore.
5. Authorize explicit new-target migration job; certify exact history/schema/runtime roles. No application auto-migrations, fakeV3 or checksum repair.
6. Certify actual new-service backup/restore/key recovery, operational monitoring/audit, RPO/RTO and tenant-aware rollback artifacts.
7. Separately approve guard replacement activation and deployment, restricted smoke writes and matched routing. Technical readiness alone does not authorize launch.
8. Separately approve actual provider credentials/DNS/webhooks/egress/controlled recipient test and verified public onboarding/worker activation; stage capacity with on-call and kill switches.
9. Preserve new customer data through rollback; stop sending after restore pending uncertain-receipt reconciliation. Never attach old unscoped FitCore to the SaaS DB.

## Outstanding security decisions

Provider integration and raw-body webhook signatures/replay/dedup/suppression remain absent. Current retry count/backoff does not bound the provider dedup window after outages/restores; add a reviewed first-attempt horizon and uncertainty policy before real delivery. Worker and independent cleanup supervisor remain unimplemented. External key custody/rotation/archive recovery, browser JWT/XSS/CSRF policy, actual trusted-proxy/direct-ingress controls and shared PostgreSQL limiter capacity, secret management/exact CORS, SQL/driver log privacy/durable audit/on-call monitoring, real runtime privileges, real backup certification and hosted CI/rules are all BLOCKED for production. A clean database waives none of them.
