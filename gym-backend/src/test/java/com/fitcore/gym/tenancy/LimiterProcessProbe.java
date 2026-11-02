package com.fitcore.gym.tenancy;

/** Test-only independent process. No real connection inputs accepted. */
public class LimiterProcessProbe {
 public static void main(String[] args)throws Exception {
  if(args.length!=2 || !args[0].matches("tenant_[a-f0-9]{32}") || !args[1].matches("jvm-[a-f0-9-]{36}"))throw new IllegalArgumentException();
  var fixture=new FoundationMigrationTest();try(var ignored=fixture.connection()){}
  var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(System.getenv("FITCORE_POSTGRES_TEST_URL")+"?currentSchema="+args[0],System.getenv("FITCORE_POSTGRES_TEST_USERNAME"),System.getenv("FITCORE_POSTGRES_TEST_PASSWORD"));
  var limiter=new com.fitcore.gym.security.SharedAbuseLimiter(new org.springframework.jdbc.core.JdbcTemplate(ds),new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
  int accepted=0;for(int n=0;n<50;n++)if(limiter.allow(args[1],"two-process-peer"))accepted++;
  System.out.print(accepted);
 }
}
