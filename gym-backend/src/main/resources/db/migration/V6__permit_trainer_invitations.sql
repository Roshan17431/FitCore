ALTER TABLE gym_invitations DROP CONSTRAINT gym_invitations_role_check;
ALTER TABLE gym_invitations ADD CONSTRAINT gym_invitations_role_check CHECK(role IN ('MEMBER','TRAINER'));
