-- Security state and its authorization version change in the same row operation.
-- This also covers controlled SQL updates so disable/re-enable cannot revive a JWT.
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';
ALTER TABLE users ADD CONSTRAINT nonnegative_authz_version CHECK(authz_version>=0);
CREATE FUNCTION fitcore_version_security_change() RETURNS trigger LANGUAGE plpgsql AS $version$
BEGIN
 IF NEW.role IS DISTINCT FROM OLD.role OR NEW.email IS DISTINCT FROM OLD.email
    OR NEW.enabled IS DISTINCT FROM OLD.enabled OR NEW.gym_id IS DISTINCT FROM OLD.gym_id
    OR NEW.password_hash IS DISTINCT FROM OLD.password_hash THEN
  NEW.authz_version := OLD.authz_version+1;
 ELSIF NEW.authz_version < OLD.authz_version THEN
  RAISE EXCEPTION 'Authorization version cannot decrease' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $version$;
CREATE TRIGGER version_security_changes BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION fitcore_version_security_change();
