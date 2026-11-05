package com.fitcore.gym.tenancy;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** V9 preserves synthetic V8 history and supplies explicit, non-seeding catalog defaults. */
@EnabledIfEnvironmentVariable(named="FITCORE_TENANCY_TEST_TOKEN",matches="[a-f0-9]{64}")
class CatalogMigrationTest {
    final FoundationMigrationTest fixture=new FoundationMigrationTest();
    @BeforeEach void setup()throws Exception{fixture.isolatedSchema();}
    @AfterEach void cleanup()throws Exception{fixture.cleanup();}
    @Test void emptyCatalogInitializationRequiresVersionedArchiveAndPublicationFields()throws Exception{
        fixture.migrate("latest");
        assertThat(fixture.value("select version from flyway_schema_history order by installed_rank desc limit 1")).isEqualTo("10");
        assertThat(fixture.number("select count(*) from information_schema.columns where table_schema=current_schema() and (table_name,column_name) in (('exercises','archived'),('workout_plans','status'),('nutrition_plans','archived')) and is_nullable='NO' and column_default is not null")).isEqualTo(3);
        assertThat(fixture.number("select count(*) from gyms")).isZero();assertThat(fixture.number("select count(*) from users")).isZero();
    }
    @Test void upgradingV8KeepsExistingCatalogVisibilityAndEveryHistoricalValue()throws Exception{
        fixture.twoGyms();fixture.migrate("8");
        String memberships=fixture.value("select string_agg(row_to_json(x)::text,'|' order by id) from active_memberships x");
        String sessions=fixture.value("select string_agg(row_to_json(x)::text,'|' order by id) from trainer_sessions x");
        fixture.migrate("latest");
        assertThat(fixture.number("select count(*) from exercises where not archived")).isEqualTo(2);
        assertThat(fixture.number("select count(*) from workout_plans where status='PUBLISHED'")).isEqualTo(2);
        assertThat(fixture.number("select count(*) from nutrition_plans where not archived")).isEqualTo(2);
        assertThat(fixture.value("select string_agg(row_to_json(x)::text,'|' order by id) from active_memberships x")).isEqualTo(memberships);
        assertThat(fixture.value("select string_agg(row_to_json(x)::text,'|' order by id) from trainer_sessions x")).isEqualTo(sessions);
    }
}
