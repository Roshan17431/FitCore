SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';
ALTER TABLE gym_invitations ADD CONSTRAINT uq_invitation_gym_id UNIQUE(gym_id,id);
CREATE TABLE email_outbox (
 id uuid PRIMARY KEY, gym_id uuid REFERENCES gyms(id), challenge_id uuid, invitation_id uuid,
 kind varchar(32) NOT NULL CHECK(kind IN ('OWNER','VERIFY','MEMBER_INVITE','TRAINER_INVITE','RESET','PASSWORD_CHANGED')),
 key_id varchar(32), payload bytea,
 status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','PROCESSING','ACCEPTED','DEAD','CANCELLED')),
 attempts int NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 5),
 next_attempt_at timestamp(6) NOT NULL, expires_at timestamp(6) NOT NULL,
 lease_until timestamp(6), lease_token uuid, accepted_at timestamp(6), failure_code varchar(32),
 created_at timestamp(6) NOT NULL, completed_at timestamp(6),
 FOREIGN KEY(gym_id,invitation_id) REFERENCES gym_invitations(gym_id,id),
 CHECK(invitation_id IS NULL OR gym_id IS NOT NULL),
 CHECK((status IN ('PENDING','PROCESSING') AND payload IS NOT NULL AND key_id IS NOT NULL) OR (status IN ('ACCEPTED','DEAD','CANCELLED') AND payload IS NULL AND key_id IS NULL)),
 CHECK((status='PROCESSING' AND lease_until IS NOT NULL AND lease_token IS NOT NULL) OR (status<>'PROCESSING' AND lease_until IS NULL AND lease_token IS NULL))
);
CREATE INDEX ix_outbox_due ON email_outbox(next_attempt_at,created_at) WHERE status IN ('PENDING','PROCESSING');
CREATE INDEX ix_outbox_challenge ON email_outbox(challenge_id);
CREATE INDEX ix_outbox_invitation ON email_outbox(gym_id,invitation_id);
CREATE INDEX ix_outbox_retention ON email_outbox(completed_at);
