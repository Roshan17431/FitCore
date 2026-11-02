-- Additive only. No existing email is presumed verified; no ownership mapping.
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';
ALTER TABLE users ADD COLUMN email_verified_at timestamp(6);
CREATE TABLE account_challenges (
 id uuid PRIMARY KEY, token_hash varchar(64) NOT NULL UNIQUE,
 purpose varchar(32) NOT NULL CHECK(purpose IN ('OWNER','INVITE','VERIFY','RESET')),
 email varchar(254) NOT NULL, user_id uuid, gym_id uuid, authz_version bigint,
 full_name varchar(100), password_hash varchar(255), gym_name varchar(100), slug varchar(63),
 fitness_goal varchar(255), invitation_hash varchar(64),
 expires_at timestamp(6) NOT NULL, consumed_at timestamp(6), created_at timestamp(6) NOT NULL,
 FOREIGN KEY (gym_id,user_id) REFERENCES users(gym_id,id),
 CHECK ((purpose IN ('VERIFY','RESET') AND user_id IS NOT NULL AND gym_id IS NOT NULL AND authz_version IS NOT NULL)
     OR (purpose IN ('OWNER','INVITE') AND user_id IS NULL AND authz_version IS NULL)),
 CHECK (purpose NOT IN ('OWNER','INVITE') OR (full_name IS NOT NULL AND (consumed_at IS NOT NULL OR password_hash IS NOT NULL))),
 CHECK (purpose <> 'OWNER' OR (gym_name IS NOT NULL AND slug IS NOT NULL)),
 CHECK (purpose <> 'INVITE' OR invitation_hash IS NOT NULL)
);
CREATE INDEX ix_challenges_email_purpose ON account_challenges(email,purpose);
CREATE INDEX ix_challenges_expiry ON account_challenges(expires_at);
CREATE INDEX ix_challenges_account ON account_challenges(gym_id,user_id);
CREATE TABLE security_rate_limits (
 scope_hash varchar(64) PRIMARY KEY, bucket bigint NOT NULL, attempts int NOT NULL CHECK(attempts>0),
 expires_at timestamp(6) NOT NULL
);
CREATE INDEX ix_security_rate_expiry ON security_rate_limits(expires_at);
-- Verification belongs to the current address, including controlled SQL/admin updates.
CREATE FUNCTION fitcore_clear_email_verification() RETURNS trigger LANGUAGE plpgsql AS $email$
BEGIN
 IF NEW.email IS DISTINCT FROM OLD.email THEN NEW.email_verified_at := NULL; END IF;
 RETURN NEW;
END $email$;
CREATE TRIGGER clear_email_verification BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION fitcore_clear_email_verification();
