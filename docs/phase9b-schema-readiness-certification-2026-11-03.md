# Phase 9B — clean database schema readiness certification

3 November2026 (Asia/Kolkata). Local-only work on `codex/phase9a-clean-instance`, starting at `ddf71b6` plus preserved Phase9A implementation. No source commit, push, merge, provisioning, production database access, persistent migration, real email or deployment.

**Schema certification: PASS locally. Persistent/hosted SaaS demonstration and production release: BLOCKED.** Schema certification and permission to connect/start against a persistent database are separate gates. The production startup prohibition remains unchanged.

## Root cause and exact correction

The existing allowlist covered staged legacy archive restores followed by forward migrations. It omitted both the entirely fresh V8 schema and a full archive/restore after V8. PostgreSQL18 parses/rendered array casts differently across those paths, producing different complete descriptor hashes for equivalent constraints/index predicates.

Only two exact fingerprints were added to `SchemaContract.ACCEPTED`:

| Certified path | Newly accepted SHA256 |
| --- | --- |
| Fresh V1,V2,V4,V5,V6,V7,V8 | `6953167a756786a1de56dd68acb7940e5ae02a710d803d5f18cc737f380f24ca` |
| Full V8 custom-archive restore | `7f870102345b628218d6575c1c1dbe67408ad05b69e0d5c90a1d9d5ec6fefc1c` |

Both previous contracts remain accepted. The production descriptor SQL, hashing/serialization, `ReleaseReadiness`, all tenant/security checks, V1–V8 and startup guard are unchanged. No CHECK/index/function/trigger is ignored, no general normalization is introduced into production, no V3 is created, and no additive migration was necessary: the verified differences are representations, not changed business DDL.

## Independent verification before accepting hashes

Three independently marked harness-owned PostgreSQL18.6 containers supplied fresh V8, full V8 restore and the previously accepted V6-archive-restore→V8 reference. The reference fingerprint is `260256f9538c02f81352fd57fe541eaf95f53d8db171ecbd0fdac535fe27c2f7`. No customer records or production ownership mapping were used; the reference retains the existing explicit synthetic mapping fixture, while clean initialization requires no V3. All records are synthetic.

Test-only inventory captures982 structural records per path:

| Metadata category | Records per path |
| --- | --- |
| Columns/types/defaults/precision/nullability/identity/generated/collation |194|
| Constraints including definition/validation/deferral |270|
| Index definitions/predicates/validity/readiness/uniqueness |78|
| Internal FK/constraint triggers and enabled/deferral state |260|
| User triggers with table+name identity and enabled/deferral state |27|
| Function definitions/security/configuration attributes |7|
| Relations, partition/relation options and RLS flags |118|
| Sequence type/options |14|
| Sequence→table.column identity ownership dependencies |14|

All paths have identical object keys. Policies are explicitly queried and absent in all three. Each sequence ownership mapping points to the expected table's `id` with identity dependency `i`. The collector rejects duplicate identities; repeated trigger names are keyed by their relation as well as name.

A separate read-only reviewer independently compared all maps and reproduced the two proposed hashes and existing reference hash using the unchanged production serialization. No substantive integrity difference was found. Expanded inventory and SQL semantics tests passed BEFORE the allowlist change; three-path Flyway validation ran successfully. Later restricted-login positive/negative tests verify permissions separately. Independent review is local engineering evidence, not an operator/security approval for production.

### Exact structural differences

Fresh↔full restore:13 object records differ. Fresh↔accepted reference:6. Full restore↔accepted reference:7. Only these categories differ:

1. Nine CHECK definitions have equivalent array-cast rendering:
   - `gyms.gyms_status_check`
   - `users.users_role_check`
   - `gym_invitations.gym_invitations_role_check`
   - `account_challenges.account_challenges_purpose_check`
   - `account_challenges.account_challenges_check`
   - `account_challenges.account_challenges_check1`
   - `email_outbox.email_outbox_kind_check`
   - `email_outbox.email_outbox_status_check`
   - `email_outbox.email_outbox_check1`
2. Partial index `email_outbox.ix_outbox_due` has the same array-cast rendering change in its predicate.
3. Raw `proconfig` of `fitcore_assert_owner(uuid)`, `fitcore_check_owner()` and `fitcore_scope_catalogs()` differs only in quoting/spacing of the same `search_path`. Their full `pg_get_functiondef` and all other captured attributes match. This raw-setting difference is additional audit evidence; it is not a hash contributor because production hashes `pg_get_functiondef`.

Representative fresh expression: `ANY ((ARRAY['MEMBER'::character varying, 'TRAINER'::character varying])::text[])`.
Restored expression: `ANY (ARRAY[('MEMBER'::character varying)::text, ('TRAINER'::character varying)::text])`.

The literals, target text type, equality/inequality operators, predicate structure, validated flags and deferral settings are unchanged. PostgreSQL SQL truth-table tests evaluate every encountered enum array with valid values, unknown/lowercase/empty/Unicode values and NULL for both `= ANY` and `<> ALL`; results are identical including SQL NULL behavior. Resolved function search paths match across all three databases.

Test-only comparison translates only those exact named objects/fields into the reviewed alternate rendering and requires every other metadata record to match exactly. This helper is not called by production certification. Production still compares an unmodified full SHA256 against four exact contracts.

## Privileges, history and negative cases

Fresh and restored positives use an actual non-owner login (`NOSUPERUSER`, `NOCREATEDB`, `NOCREATEROLE`, `NOBYPASSRLS`, `NOINHERIT`), not a superuser or only `SET ROLE`. Effective checks independently require every DML privilege on26 application tables, required sequence USAGE/SELECT, history SELECT only, no object-owner membership, schema CREATE, TRUNCATE/REFERENCES/TRIGGER grants, sequence UPDATE, history mutation or helper EXECUTE. Synthetic owner/migrator credentials are confined to fixture setup/restore and mutation injection.

Flyway validates resolved migration checksums/history on all three paths. Runtime readiness separately checks its existing exact version/order/success contract `1,2,4,5,6,7,8`. Runtime does not acquire migration or schema-owner credentials. Checksum provenance remains the independent Flyway/release-certification step; this correction does not add dynamic checksum validation or claim that the readiness probe audits all PostgreSQL permissions/metadata.

The certification suite exercises18 rollback-scoped schema/privilege mutations on BOTH fresh and restored paths (36 cases):

- Unknown table/column, removed same-gym membership-plan FK, relaxed user-role CHECK.
- Changed outbox partial-index predicate and relaxed outbox-kind CHECK.
- Disabled immutable-gym, preserve-primary-owner and valid-primary-owner triggers; nullable user ownership.
- Incorrect history version or unsuccessful history row.
- Excess schema CREATE, history UPDATE, helper EXECUTE, superuser status or membership in fixture-owner role; missing sequence USAGE.

Each path is asserted READY before the mutation, NOT_READY after it, and mutations are rolled back. Separate unknown-schema tests verify the public readiness boundary fails closed on missing objects/errors.

Actual runtime SQL also rejects invalid gym status, user role, invitation OWNER role, outbox kind/status and a cross-gym membership-plan reference on both paths (12 cases). Existing broader security tests retain other role/reference/owner/booking/credential protections. The final gate reruns clean initialization, two-gym verified onboarding, backup/table/sequence reconciliation, encrypted outbox recovery and actual restored HTTP isolation. Fresh/restored readiness now returns true; actual clean/restored HTTP readiness returns200 without changing guards.

## Verification record

Final `./scripts/verify-release.sh` exited0:

| Verification | Result |
| --- | --- |
| Backend regressions/security/PostgreSQL/recovery | **954 passed; zero failures/errors/skips** (944 preserved +10 certification cases) |
| Frontend contracts/session/roles | **35 passed** |
| Browser workflows | **44 distinct passed**;56 executed across both harness runs including12 repeated transaction/security groups |
| Release-result gate regressions | **8 passed** |
| Frontend lint/build | **PASS / PASS** |
| Focused clean HTTP/recovery/certification | **13 passed** |
| Independent descriptor/semantics before correction | **2 passed**, all three Flyway checksum validations successful |
| Fresh/restored restricted-login schemaReady | **true / true** |
| Fresh/restored actual HTTP readiness | **200 / 200** |

Initial RED evidence reproduced rejected fresh/restored readiness before correction. The missing-certification-suite gate regression also failed before required-suite registration and passed after it. This is not a relaxed test expectation: the negative matrix requires both accepted baselines to pass before each intentional drift is checked.

Browser breakdown:8 transactional,10 role/module,3 analytics/CSV isolation,16 owner/catalog/session,3 gate-off and4 security. Instrumented role/isolation/owner collectors recorded zero uncaught page errors/external requests. Both harness runs verified ports/process/container cleanup. Independent final review found no actionable issue; its minor separate-ACL assertion feedback was corrected to four individual DML checks and included in the full gate. Local success does not certify hosted CI or production recovery.

Evidence remains outside Git in sibling `evidence/phase9b-*.log` and descriptor JSON files. The test writes its synthetic structural inventory to ignored `gym-backend/target/schema-certification-evidence.json`; it contains no customer rows, credential hashes, tokens or outbox payloads. The final inventory is copied into sibling evidence for review. Browser artifacts remain ignored. No remote hosted CI success is claimed.

## Files changed for Phase 9B

- `gym-backend/src/main/java/com/fitcore/gym/operations/SchemaContract.java`: only explanatory comment and two exact accepted hashes.
- `gym-backend/src/test/java/com/fitcore/gym/tenancy/SchemaDescriptorSnapshot.java` (new): independent structural inventory.
- `gym-backend/src/test/java/com/fitcore/gym/tenancy/SchemaReadinessCertificationTest.java` (new): descriptor/semantics, actual-runtime positives and fail-closed negatives.
- `gym-backend/src/test/java/com/fitcore/gym/tenancy/CleanInstanceHttpTest.java`: replace historical rejection with readiness acceptance plus HTTP200.
- `gym-backend/src/test/java/com/fitcore/gym/tenancy/CleanInstanceRecoveryTest.java`: require restricted restored readiness and actual HTTP200, retaining all isolation/recovery assertions.
- `scripts/check_release_results.py`, `scripts/test_release_results.py`: new critical suite and missing-suite regression.
- `docs/superpowers/plans/2026-11-03-phase9b-schema-readiness.md`, this report and release-checklist companion entry.

Existing uncommitted Phase9A browser checks/design/report/plan remain preserved. Its report is historical: Phase9B supersedes only its clean-fingerprint readiness blocker. Original Phase8/9 documents/private worksheets remain untouched in the other worktree. No migration, startup guard, business rule, credential or hosting configuration changed.

## Final scope and remaining demonstration/release blockers

Local disposable two-gym workflows and schema certification can be demonstrated with the existing owned harness. Hosted or persistent demonstration remains BLOCKED: the startup prohibition rejects persistent targets even when their schema is certified. Its separate future production-target/authorization design is [Phase9A environment design](phase9a-clean-environment-design.md); no activation mechanism was implemented here.

Unresolved before a separate hosted SaaS: approved target identity/region/budget, restricted real roles and secrets/TLS/CORS, reviewed startup mechanism and deployment authorization, browser JWT/XSS/CSRF decision, proxy/shared-limit policy, provider/webhooks/suppression and bounded idempotency horizon, outbox key custody/rotation/recovery, supervised worker/cleanup, actual backup/restore/RPO/RTO, monitoring/audit/on-call, hosted CI/required checks and immutable artifacts. Owner catalog configuration remains limited to membership plans; exercise/workout/nutrition write APIs/UI require a product decision. Public onboarding and real mail remain disabled until separately approved.

Owner decisions next: approve the reviewed local correction; select local-only demonstration versus separately funded/authorized hosted preparation; decide catalog scope; assign security/recovery/infrastructure approvers and targets. No persistent database access, hosting change or deployment follows automatically from a PASS on schema readiness.
