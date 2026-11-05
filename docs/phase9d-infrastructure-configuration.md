# Phase 9D — independent infrastructure and configuration registry

4 November2026. **Design only; all future resource identities, domains, secret references and plans need approval.** No live values are included. No resource was provisioned or inspected through Render/Vercel, and their existing projects must remain unchanged. Source baseline45782b8.

## New resource boundary

| Resource | Proposed purpose / validation | Safe initial state |
| --- | --- | --- |
| New Vercel project | Vite frontend plus fixed-upstream BFF; independent project/deployment identity, approved HTTPS public origin, source/artifact digest and exact new Render upstream. No preview routes to live DB/backend. | Git auto-deployment held for **all** branches; no provider/JWT/DB secrets in browser build; no existing project reuse. |
| New Render web service | Account/session/TenantActor and unchanged business API; binds0.0.0.0:$PORT; new service/environment identity and authenticated BFF boundary. | Auto-Deploy Off, no traffic/public onboarding, no migrated credentials; current guard still rejects persistent startup. |
| New PostgreSQL18 service/database | Empty logical application schema, signed target identity, authenticated TLS, explicit roles/ACLs, backups and capacity certification. | No copy/import/legacyV3; network deny except separately approved runtime/migration/recovery paths; do not accept provider's broad public defaults. |
| New mail worker | Reviewed app worker entrypoint, new workload lease, restricted DB/provider/key scope, bounded polling and SIGTERM. | No worker/provider credentials or sending; worker disabled until receipt/horizon/key/quotas/recovery evidence passes. |
| New maintenance/cleanup job | Bounded UTC job independent of delivery; scoped DB role, retention contract and one shared maintenance lease. | Unscheduled until approved; sends nothing; no public trigger route. |
| Release authority / secret custody | Independent signing/verifying roots, protected operator approvals, current target/recovery epoch, artifact/config receipts, secret version delivery. | No deployment permit issued; trust/bootstrap mechanism must be implemented/reviewed before any new persistent target. |
| Backup/audit/monitoring | Separate encrypted storage, retention/access, immutable receipt/audit integrity, alert destinations and named on-call. | No old FitCore bucket, DB, key, secret group, project, domain or deploy hook reused. |

Minimum infrastructure cost includes the always-on web service/database, a BFF request hop, worker/cleanup, key/backup/monitoring and provider quotas. No current plan/price is assumed. Owner supplies region/residency, availability/SLO, spend caps and expected load; capacity and latency are then measured. A free template is not evidence of a production-grade backup/worker plan.

### TLS and network selection gate

Render private networking requires same workspace/region; Vercel is not automatically on it. Keep Vercel→Render over authenticated HTTPS; authenticate the BFF independently of CORS/peer IP. Render workers can initiate DB/provider calls but do not receive HTTP. [Render private-network documentation](https://render.com/docs/private-network).

Current Render internal PostgreSQL TLS uses self-signed certificates without verify-full support; earlier9A's authenticated internal TLS cannot be claimed. Retain hostname/CA verification in this proposal. A supported external Render endpoint or another new PostgreSQL18 provider is a **candidate**, subject to later driver/CA/SNI/network/region approval and connection tests. Render documents managed external TLS and broad default external access; it does not establish the chosen JDBC trust setup for this project. No downgrade to require/trust-all is approved. If the owner wants an internal exception/pinning design, obtain a separate security review including rotation and endpoint-impersonation tests. [Render connections/TLS/access](https://render.com/docs/postgresql-creating-connecting).

Do not change old services' allowlists/DNS. Approve new-resource ingress/egress explicitly. Use exact supported provider endpoints and permit-listed services; IP allowlisting alone cannot authenticate a tenant or gateway. Require strict outbound HTTPS certificate verification. Freeze target/config while certifying; legitimate failover or secret rotation must follow the signed target policy, not an unreviewed host alias.

## Database identities and privilege contract

These are logical role names, not created roles or credentials. Actual identifiers are owner-approved and included in the signed target manifest.

| Identity | Authority | Forbidden in runtime |
| --- | --- | --- |
| Schema owner | NOLOGIN owns application objects; controlled migration job can SET ROLE through separately held authority. | No membership, inheritance or SET ROLE path from web/worker runtime. |
| Migration login | Short-lived explicit job only; exact approved target, advisory deployment lock, reviewed DDL/versioned migration files and ACL reconciliation. | Never supplied to application/BFF/worker; no app startup migration, clean, baseline or repair. |
| Web runtime login | CONNECT/approved schema USAGE; exact application table DML and necessary sequence USAGE/SELECT; history SELECT only; tightly defined future session/challenge/receipt access. | No super/createDB/createRole/bypassRLS/replication, object ownership, schema/database CREATE or unapproved TEMP, TRUNCATE/REFERENCES/TRIGGER, sequence UPDATE/ownership, history mutation, unsafe helper EXECUTE, PUBLIC/default privilege surprises, unapproved other-schema access. |
| Worker/cleanup login | Explicit smallest tables/functions needed for claims/receipt/suppression or retention; inspect security bindings through reviewed functions/views as necessary. | No gym membership/booking/catalog mutation or migration authority. No widening to full web runtime simply to satisfy today's26-table readiness check. |
| Recovery operator | Time-bounded audited ability to create isolated restored target, restore owner/ACL state and perform approved revocation/reconciliation. | No BFF/public API role; operator cannot approve its own release/backup evidence. |

Current ReleaseReadiness certifies a web-runtime26-table contract only. A future worker/maintenance role needs its own strict readiness contract; do not weaken the web check to accommodate it. Explicitly review security-definer functions, owner/search_path and function PUBLIC EXECUTE. Reset/recreate roles and GRANTs **and REVOKEs** after restore; NOINHERIT does not alone prohibit SET ROLE membership. Validate session_user and current_user through the actual pool.

No RLS policy currently exists. This design preserves application/repository tenancy and composite FKs; runtime credentials remain a powerful cross-gym boundary. RLS would be a separate design, not an untested claim here. Unknown object/role/default ACL/sequence drift fails certification.

## Configuration naming rules

**CURRENT** rows are consumed today. **PROPOSED** keys do not exist yet and cannot enable anything in this checkout. Values are described, never populated. Exact Spring keys are canonical; do not guess underscore aliases for hyphenated keys. Future binding/precedence tests must validate deployment encoding. No executable .env, Render Blueprint or Vercel project config is generated in this phase.

All future security configuration is included in the signed configuration digest. Secrets are represented by protected manager reference/version, never raw manifest/browser/log values. A changed role/secret version/target/keyring/mode needs a new authorization. Missing required configuration fails closed; production cannot inherit test/old application defaults.

### Existing keys and required production validation

| Exact current key | Current source/default | Proposed certified-mode rule |
| --- | --- | --- |
| `spring.datasource.url`, `.username`, `.password` | DATABASE_URL / DATABASE_USERNAME / DATABASE_PASSWORD, mandatory | Disposable only as currently. Future certified connection is constructed from sealed target and secret resolver; reject these as alternate/overriding selectors rather than probing a second unapproved connection. |
| `spring.datasource.driver-class-name` | org.postgresql.Driver | Exact reviewed driver/artifact; no alternate class/SSL factory. |
| `spring.datasource.hikari.schema` | Not explicitly configured; used by local fixtures | Never an independent override; exact initial schema/search_path from signed target, verified on every pool connection. |
| `spring.jpa.hibernate.ddl-auto` | validate | Must remain validate. |
| `spring.sql.init.mode` | SQL_INIT_MODE, default never; demo profile overrides always | Must be never; no demo profile on persistent target. |
| `spring.flyway.enabled`, `.baseline-on-migrate`, `.clean-disabled` | false / false / true | Application remains off/false/true. Separately authorized migration job uses a separate process/identity/config. |
| `jwt.secret` | JWT_SECRET, mandatory | New independently scoped signing material only; never BFF/browser build. If bearer remains supported, add approved ring/issuer/audience/lifetime policy. |
| `jwt.expiration-ms` | JWT_EXPIRATION_MS, default86400000 | Not an approved production lifetime; proposal900000 for approved bearer mode, startup-validated bound. Cookie lifetime is separate. |
| `jwt.allowed-issue-clock-skew-seconds` |30; code accepts0..120 | Approve bounded clock policy; monitor clock health; never relax expiry after restore. |
| `cors.allowed-origins` | CORS_ALLOWED_ORIGINS, old Vercel + local defaults | Exact new HTTPS origin only; cookie-BFF mode has no cross-origin browser API trust. No wildcard, old/local/preview origins or credentials=true as a shortcut. |
| `server.forward-headers-strategy` | none; alternatives rejected by guard | Keep none; authenticated gateway client identity handled separately. |
| `server.port` | PORT, default8080 | Approved platform port; bind0.0.0.0; never a target identity or approval signal. |
| `spring.jpa.show-sql`, `spring.jpa.properties.hibernate.format_sql` | JPA_SHOW_SQL / JPA_FORMAT_SQL, bothfalse | false; SQL/driver errors/bind logging must be separately redacted/suppressed. |
| `fitcore.onboarding.demo-enabled` | false | false on certified target; never reuse demo activation as production authorization. |
| `fitcore.security-flows.demo-enabled` | false | false on certified target; future approved activation mode has a separate path. |
| `fitcore.security.public-origin` | http://127.0.0.1:5175 | Mandatory exact new public HTTPS origin, no userinfo/path/query/fragment; matches CSRF/BFF/links, never derived from Host/XFF. |
| `fitcore.email.provider` | disabled; any other value fails startup | Remains disabled until reviewed adapter and explicit provider authorization. Current readiness rejects a non-disabled provider. |
| `fitcore.email.outbox.enabled`, `.worker-enabled` | false / false | Queue requires valid external key inventory; worker separately gated, never inferred from queue enabled. |
| `fitcore.email.outbox.keys`, `.active-key` | Empty keyring by default; required when queue enabled | Existing semicolon keyId=base64Key interface stays test-compatible; production resolver supplies protected versioned keys, including all retained ciphertext/backup needs. Never place keyring in Vite or source. |
| `fitcore.catalog.image-origins` | Empty by default | No external image origins until exact HTTPS allowlist approval. S3/upload remains disabled. |
| `aws.s3.enabled` | AWS_S3_ENABLED false; true rejected | false. Existing AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY / AWS_REGION / AWS_BUCKET_NAME are not required; do not load or copy old values. |
| `VITE_API_BASE_URL` | Current frontend requires a nonempty base string and appends /api; no absolute/HTTPS parser | New cookie-mode adapter uses relative /api and rejects an absolute backend fallback. Existing bearer disposable build remains explicit. All VITE_* values are public. |

Known test-only names `FITCORE_TENANCY_TEST_TOKEN`, `FITCORE_POSTGRES_TEST_URL`, `_USERNAME`, `_PASSWORD`, `_IMAGE` and `FITCORE_VERIFY_*_UI` belong only to owned disposable harnesses. They are forbidden in certified environments, not a shortcut to “certified” deployment. The root legacy `JPA_DDL_AUTO` template value is stale/unconsumed and must not become a new way to request schema updates.

### Proposed runtime and gateway keys — not implemented

| Proposed exact name | Purpose / validation | Safe default or initial proposal |
| --- | --- | --- |
| `fitcore.instance.mode` | Typed disposable-only or certified-new-saas; latter requires the complete authorization chain | disposable-only; mode alone grants nothing |
| `fitcore.release.trust-bundle-path` | Pinned independently controlled approval public keys/policy; not supplied by approval payload | absent → no certified startup |
| `fitcore.release.permit-path` | Protected signed target/artifact/config/operation permit | absent → no certified startup |
| `fitcore.release.lease-authority-origin` | Exact approved HTTPS control endpoint and authenticated bootstrap; not a user URL | absent → no certified startup |
| `fitcore.release.lease-max-seconds`, `.renew-seconds` | Maximum bounded workload lease / renewal, bound to approval |900 /300; expired or unavailable renewal fails closed |
| `fitcore.database.runtime-secret-ref` | Secret manager resource/version must equal permit; raw runtime password comes only from resolver | absent → no certified connection |
| `fitcore.database.pool.maximum-size`, `.limiter-maximum-size` | Typed positive pool budgets included in capacity/config certificate; not alternate targets/credentials | no production size until approved capacity |
| `fitcore.browser.transport` | bearer-disposable or cookie-bff; no silent production fallback | bearer-disposable; certified mode requires approved choice |
| `fitcore.browser.session.idle-seconds`, `.absolute-seconds` | Positive idle≤absolute with tested maximums |1800 /43200 proposed |
| `fitcore.browser.session.remember-enabled` | Persistent cookie policy | false; no remember option initially |
| `fitcore.browser.cookie-name` | Fixed host-only secure authenticated cookie; only serialized login/logout may mutate it | __Host-fitcore_session; reject unsafe alternate prefix/Domain/path |
| `fitcore.browser.preauth-cookie-name` | Separate anonymous CSRF bootstrap handle, never authenticated identity | __Host-fitcore_preauth; Secure/HttpOnly/Lax/Path=/, no Domain |
| `fitcore.browser.csrf.enabled` | Framework synchronizer validation for cookie mode | true; cookie-certified mode rejects false |
| `fitcore.security.activation-mode` | disabled or verified-public, bound to release scope; does not set demo flags | disabled |
| `fitcore.security.require-verified-email` | Every new human account path, including trainers/email changes | true in certified policy |
| `fitcore.gateway.trust-bundle-path`, `.audience` | Approved BFF signing keys and instance-bound audience | absent → reject gateway requests |
| `fitcore.gateway.max-age-seconds`, `.clock-skew-seconds`, `.nonce-retention-seconds` | Strict signed assertion expiry/skew and shared replay rejection |30 /5 /120 proposed; validate retention covers validity+skew |
| `fitcore.jwt.issuer`, `.audience`, `.keyring-secret-ref`, `.active-key-id` | New instance JWT binding/rotation if retained bearer/internal path needs it; separate from session and outbox | absent → no certified bearer issuance |
| `fitcore.recovery.account-trust-bundle-path` | Purpose-restricted account-reactivation verification keys, distinct receipt type/audience from release/gateway proofs; current epoch supplied by external authority | absent → restored identities remain authentication-held |
| `fitcore.observability.audit-sink-ref` | Protected append-only event sink, tenant-aware access, no secrets/addresses/raw IP | absent → release blocked; no fabricated silent audit success |

The signed manifest itself supplies immutable instance/resource IDs, target host/port/database/schema/TLS policy, expected users/ACL contract, artifact/migration digests, receipt IDs and recovery epoch described in the design. These are **manifest fields**, not additional free-form environment overrides. An arbitrary expected-instance string cannot prove workload identity.

### Proposed BFF server/build variables — not implemented

| Proposed name | Purpose / validation | Safe default |
| --- | --- | --- |
| `VITE_AUTH_TRANSPORT` | Public build selection; only reviewed cookie-bff artifact permitted for recommended production mode | bearer-disposable until approved build; not an activation secret |
| `FITCORE_BFF_PUBLIC_ORIGIN` | Exact new browser origin matching signed release and backend security.public-origin | absent → BFF refuses API |
| `FITCORE_BFF_BACKEND_ORIGIN` | Fixed new Render HTTPS origin; no path/query/userinfo, no request-supplied destination | absent → BFF refuses API |
| `FITCORE_BFF_SIGNING_KEY_REF`, `FITCORE_BFF_SIGNING_KEY_ID` | Server-only protected asymmetric key version; backend has public verification keys only | absent → no gateway signing |
| `FITCORE_BFF_RELEASE_PERMIT_REF` | Protected workload/release authorization reference; validate backend/frontend/config identities | absent → no certified BFF |
| `FITCORE_BFF_UPSTREAM_TIMEOUT_MS`, `FITCORE_BFF_MAX_BODY_BYTES` | Positive approved deadline/body cap; preserve legitimate API/CSV workflows; no auto-replay | proposed20000 /1048576 for normal JSON; webhook dedicated cap/policy |

No DATABASE_*, JWT_, outbox encryption/provider secret, gateway signing private key or operator permit enters a VITE_* variable. Vercel Preview/Development never inherit new production backend/key/secret references. No mutable data/JWT/session caching at CDN; Set-Cookie/no-store/CSV headers are contract-tested.

### Proposed email/abuse/maintenance keys — not implemented

| Proposed exact key | Purpose and expected validation | Safe default / initial proposal |
| --- | --- | --- |
| `fitcore.email.delivery-authorized` | Signed permit must also authorize provider/sender/egress; not sufficient alone | false |
| `fitcore.email.provider-secret-ref`, `.webhook-secret-ref` | New provider account API/signature key versions, separately scoped | absent; no sending/verified webhook ingress |
| `fitcore.email.provider-origin`, `.provider-account-id` | Fixed approved HTTPS API and immutable account/dedup scope | absent; no requests |
| `fitcore.email.sender`, `.reply-to` | Approved authenticated domain identities; reject CRLF, freeze with each intent | absent; enqueue/dispatch blocked for real delivery |
| `fitcore.email.http.connect-timeout-ms`, `.total-timeout-ms`, `.max-response-bytes` | End-to-end finite budget including library retries; validate≤lease safety bound |3000 /15000 /65536 proposed |
| `fitcore.email.retry-horizon-seconds` | Bounded by provider contract and security expiry; original first-attempt time immutable |82800 (23h) proposed, never greater than approved contract |
| `fitcore.email.outbox.keyring-secret-ref` | External current/historical AES256 versions; no missing-key destructive worker start | absent → queue/worker certification fails |
| `fitcore.email.worker.poll-ms`, `.max-in-flight` | Supervised bounded claims and graceful shutdown |1000 /1 proposed; worker-enabled stillfalse |
| `fitcore.email.cleanup.enabled`, `.schedule`, `.batch-size`, `.max-run-seconds` | Separate leased maintenance, UTC schedule,≤100 rows/batch and finite runtime |false / every5min UTC proposed /100 /30 |
| `fitcore.email.retention.terminal-days`, `.receipt-days`, `.webhook-days` | Bounded approved privacy/backup/provider replay retention; validate receipt/dedup covers replay window |7 queue days proposed; receipt/webhook mandatory owner-approved values, not guessed defaults |
| `fitcore.email.quota.requests-per-second`, `.burst`, `.daily`, `.monthly` | Shared provider-account admission across workers, retries included; cannot exceed plan/owner approval |0 sends until activation;2/s burst2 proposed; daily/monthly mandatory |
| `fitcore.abuse.policy-path` | Complete signed action/IP/address/issuer/gym/deployment budget policy from companion document | absent → protected public activation refused |
| `fitcore.abuse.scope-key-ref` | Separate HMAC key/version for recipient/IP scopes; overlap rotation across live windows | absent → production limiter certification fails |
| `fitcore.abuse.decision-timeout-ms` | Total store-acquisition/lock/SQL deadline; no local fallback |1000 proposed; failure503 |

Worker lease120s, maximum5 attempts, AAD format and token/security lifetimes remain fixed reviewed contracts rather than arbitrary knobs in this design. Changes require tests/migration/approval. Deployment settings for the new services are also configuration: Render Auto-Deploy Off, approved readiness HTTP path, shutdown budget, region/plan/capacity; Vercel all-branch hold, approved root/staging artifact, environment scoping, BFF route precedence and protected manual release. Exact provider Blueprint/CLI syntax is validated later; no template is applied now.

## Deployment and old-system protection

Create future isolated infrastructure definitions/staging artifacts for the **new** services; do not edit/reapply root render.yaml or remove shared Vercel main/master guards to enable the SaaS. A new project's manually authorized immutable frontend+BFF artifact/config can be built in an independent release staging root. Its project ID/digest must match authorization before deployment. No old project link file/env group/deploy hook may be imported. Keep Git auto-deploy held; successful CI is necessary but not deployment permission.

Migration runs as a separate explicitly approved one-shot job using the migration login, exact new target and signed migration artifact. Initial clean sequence is V1,V2,V4–V9, then only separately approved additive changes. Application runtime never has migration secrets. Health uses the future certified `/api/health/ready`; `/api/health` is liveness and cannot certify a new target. [Render health-check contract](https://render.com/docs/health-checks).

Pin built images/artifacts by digest, generate SBOM/dependency/security evidence, and retain compatible rollback artifacts. Current Docker/test images are tag based. Give the release job only the new project/service authority after protected approvals; no credentials in general PR verification jobs. Session/BFF/proxy/signature and certificate behavior must be rehearsed locally with synthetic peers first. Real service identities, network access, certificates, backup policy and traffic remain separate later operator approvals.
