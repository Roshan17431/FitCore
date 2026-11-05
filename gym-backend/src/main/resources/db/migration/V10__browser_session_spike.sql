-- Additive local browser spike only. No legacy ownership inference or business-row mutation.
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';
CREATE TABLE browser_session_families (
 id uuid PRIMARY KEY,
 revision bigint NOT NULL DEFAULT 0 CHECK(revision >= 0),
 pending_hash varchar(64),
 pending_kind varchar(6),
 created_at timestamptz NOT NULL,
 expires_at timestamptz NOT NULL,
 CONSTRAINT browser_family_pending_check CHECK (
  (pending_hash IS NULL AND pending_kind IS NULL) OR
  (pending_hash IS NOT NULL AND pending_hash ~ '^[a-f0-9]{64}$' AND pending_kind IS NOT NULL AND pending_kind IN ('LOGIN','LOGOUT'))),
 CONSTRAINT browser_family_expiry_check CHECK(expires_at > created_at)
);
CREATE TABLE browser_sessions (
 token_hash varchar(64) PRIMARY KEY CHECK(token_hash ~ '^[a-f0-9]{64}$'),
 family_id uuid NOT NULL REFERENCES browser_session_families(id) ON DELETE CASCADE,
 kind varchar(7) NOT NULL CHECK(kind IN ('PREAUTH','AUTH')),
 family_revision bigint NOT NULL CHECK(family_revision >= 0),
 user_id uuid,
 gym_id uuid,
 authz_version bigint,
 credentials_version varchar(64),
 csrf_token varchar(128) NOT NULL,
 used boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL,
 expires_at timestamptz NOT NULL,
 absolute_expires_at timestamptz NOT NULL,
 CONSTRAINT browser_session_identity_check CHECK (
  (kind='PREAUTH' AND user_id IS NULL AND gym_id IS NULL AND authz_version IS NULL AND credentials_version IS NULL) OR
  (kind='AUTH' AND user_id IS NOT NULL AND gym_id IS NOT NULL AND authz_version IS NOT NULL AND authz_version >= 0 AND credentials_version IS NOT NULL)),
 CONSTRAINT browser_session_expiry_check CHECK(expires_at > created_at AND absolute_expires_at >= expires_at),
 CONSTRAINT browser_session_user_gym_fk FOREIGN KEY(gym_id,user_id) REFERENCES users(gym_id,id)
);
CREATE INDEX browser_sessions_family_idx ON browser_sessions(family_id);
CREATE INDEX browser_sessions_user_idx ON browser_sessions(gym_id,user_id);
CREATE INDEX browser_sessions_expiry_idx ON browser_sessions(absolute_expires_at);
CREATE INDEX browser_families_expiry_idx ON browser_session_families(expires_at);
CREATE TRIGGER immutable_gym BEFORE UPDATE ON browser_sessions FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TABLE browser_gateway_nonces (
 nonce_hash varchar(64) PRIMARY KEY CHECK(nonce_hash ~ '^[a-f0-9]{64}$'),
 expires_at timestamptz NOT NULL
);
CREATE INDEX browser_gateway_nonces_expiry_idx ON browser_gateway_nonces(expires_at);
