package com.fitcore.gym.config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
class FoundationSafetyTest {
 @ParameterizedTest
 @ValueSource(strings={
  "spring.datasource.hikari.jdbc-url=jdbc:postgresql://production.invalid/customer",
  "spring.datasource.hikari.data-source-class-name=org.postgresql.ds.PGSimpleDataSource",
  "spring.datasource.hikari.data-source-properties.serverName=production.invalid",
  "spring.datasource.hikari.data-source-jndi=java:comp/env/customer",
  "spring.datasource.jndi-name=java:comp/env/customer",
  "spring.datasource.type=org.springframework.jdbc.datasource.DriverManagerDataSource",
  "spring.flyway.url=jdbc:postgresql://production.invalid/customer",
  "spring.flyway.jdbc-properties.serverName=production.invalid",
  "spring.flyway.driver-class-name=org.h2.Driver"
 })
 void refusesAlternativeConnectionSelectorsBeforeAnyConnection(String override) {
  new ApplicationContextRunner().withUserConfiguration(FoundationSafetyConfiguration.class)
   .withPropertyValues("spring.datasource.url=jdbc:h2:mem:isolated-regression",override)
   .run(c->assertThat(c.getStartupFailure()).hasStackTraceContaining("Alternative database connection settings are forbidden"));
 }
 @ParameterizedTest @ValueSource(strings={"SPRING_DATASOURCE_HIKARI_JDBCURL","SPRING_FLYWAY_URL"})
 void refusesInheritedDestinationOverrides(String key) {
  new ApplicationContextRunner().withUserConfiguration(FoundationSafetyConfiguration.class)
   .withInitializer(c->c.getEnvironment().getPropertySources().addFirst(
    new org.springframework.core.env.SystemEnvironmentPropertySource("systemEnvironment",
     java.util.Map.of(key,"jdbc:postgresql://production.invalid/customer"))))
   .withPropertyValues("spring.datasource.url=jdbc:h2:mem:isolated-regression")
   .run(c->assertThat(c.getStartupFailure()).hasStackTraceContaining("Alternative database connection settings are forbidden"));
 }
 @Test void refusesRemoteDatabaseBeforeInitializingApplication() {
  new ApplicationContextRunner().withUserConfiguration(FoundationSafetyConfiguration.class)
   .withPropertyValues("spring.datasource.url=jdbc:postgresql://production.invalid/customer")
   .run(c->assertThat(c.getStartupFailure()).hasStackTraceContaining("Foundation build requires an owned disposable database"));
 }
 @Test void refusesOrdinaryLocalCustomerDatabase() {
  new ApplicationContextRunner().withUserConfiguration(FoundationSafetyConfiguration.class)
   .withPropertyValues("spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/real_customers")
   .run(c->assertThat(c.getStartupFailure()).hasStackTraceContaining("Foundation build requires an owned disposable database"));
 }
 @Test void isolatedMemoryRegressionTestsRemainUsable() {
  new ApplicationContextRunner().withUserConfiguration(FoundationSafetyConfiguration.class)
   .withPropertyValues("spring.datasource.url=jdbc:h2:mem:isolated-regression")
   .run(c->assertThat(c).hasNotFailed());
 }
 @Test void refusesMemoryUrlWithInitializationScript() {
  new ApplicationContextRunner().withUserConfiguration(FoundationSafetyConfiguration.class)
   .withPropertyValues("spring.datasource.url=jdbc:h2:mem:fixture;INIT=RUNSCRIPT FROM 'https://forbidden.invalid/test.sql'")
   .run(c->assertThat(c.getStartupFailure()).hasStackTraceContaining("Foundation build requires an owned disposable database"));
 }
 @ParameterizedTest @ValueSource(strings={"native","framework"})
 void forwardedIdentityNeedsExplicitTopologyReview(String strategy){
  new ApplicationContextRunner().withUserConfiguration(FoundationSafetyConfiguration.class)
   .withPropertyValues("spring.datasource.url=jdbc:h2:mem:isolated-regression","server.forward-headers-strategy="+strategy)
   .run(c->assertThat(c.getStartupFailure()).hasStackTraceContaining("approved proxy policy"));
 }

}
