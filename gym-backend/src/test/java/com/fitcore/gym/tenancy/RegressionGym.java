package com.fitcore.gym.tenancy;
import org.junit.jupiter.api.extension.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;

/** Explicit synthetic fixture, used only by existing regression test builders. */
public class RegressionGym implements BeforeEachCallback, AfterEachCallback {
 public static final UUID ID=UUID.fromString("90000000-0000-0000-0000-000000000001");
 private static final UUID OWNER=UUID.fromString("90000000-0000-0000-0000-000000000002");
 public void beforeEach(ExtensionContext context) {
  var jdbc=SpringExtension.getApplicationContext(context).getBean(JdbcTemplate.class);
  if(jdbc.queryForObject("select count(*) from gyms where id=?",Long.class,ID)>0)return;
  jdbc.update("insert into gyms(id,name,slug,status,created_at,updated_at) values(?,'Synthetic regression gym','synthetic-regression','ACTIVE',current_timestamp,current_timestamp)",ID);
  jdbc.update("insert into users(id,gym_id,full_name,email,password_hash,role,enabled,created_at,updated_at,authz_version) values(?,?,'Synthetic owner','regression-owner@fixture.invalid','non-login-fixture','OWNER',true,current_timestamp,current_timestamp,0)",OWNER,ID);
  jdbc.update("update gyms set primary_owner_user_id=? where id=?",OWNER,ID);
 }
 public void afterEach(ExtensionContext context) {
  // H2 fixtures participate in the test rollback. PostgreSQL concurrency fixtures commit.
  if(!context.getRequiredTestClass().getSimpleName().equals("PostgresConcurrencyTest"))return;
  var jdbc=SpringExtension.getApplicationContext(context).getBean(JdbcTemplate.class);
  jdbc.update("update gyms set primary_owner_user_id=null where id=?",ID);
  jdbc.update("delete from users where id=?",OWNER);
  jdbc.update("delete from gyms where id=?",ID);
 }
}
