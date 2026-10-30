# Disposable tenant migration fixtures

Run `./scripts/verify-tenancy.sh` from gym-backend. It creates its own PostgreSQL 18 container, binds an ephemeral loopback port, marks the database with that container's ID, and removes only that container on exit. The migration tests check the database name, URL and live marker before connecting/migrating. No `.env`, application credentials, customer data, or ownership manifest is read.

`FoundationMigrationTest` creates an independently named schema for each test. Its `legacyRecords()` and `stageExplicitSyntheticMapping()` methods are the complete synthetic fixture and mapping. Their literal UUIDs, emails at fixture.invalid, hashes and dates are test inputs only. No mapping is inferred from email domains, roles, record counts or an assumed single legacy gym.

The legacy rehearsal contains shared catalogs, four accounts (including a disabled ADMIN and a trainer), three memberships including an overlap, workout history, a trainer booking, and all six collection tables. Explicit synthetic decisions select two owners, assign original catalogs, and copy a shared plan/exercise before remapping their references. Catalog constraint changes and copies share one transaction. Negative scenarios retain historical references and refuse enforcement rather than repairing them implicitly.

V1 creates the source-baseline schema. V2 expands it without choosing owners. Tests stage the explicit synthetic mapping at V2, then apply V4 to enforce final integrity. The operational V3 manifest validator/runner is intentionally deferred to task 8 of the approved plan. Never apply this incomplete version sequence to a persistent database; V3 must be completed and rehearsed before any persistent migration history exists.

The original business regression tests use explicitly gym-tagged fixtures with ORM-created H2/PostgreSQL schemas. Those tests prove business behavior; they do not prove PostgreSQL migration constraints. `FoundationMigrationTest` uses actual Flyway migrations and explicit COMMITs, and validates ORM mappings against the migrated schema. The browser harness also uses the migrated schema.
