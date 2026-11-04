package com.fitcore.gym.tenancy;

import static org.assertj.core.api.Assertions.*;
import com.fitcore.gym.operations.SchemaContract;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

/** Independently capture every current clean/upgrade/restore shape before accepting V9 hashes. */
@EnabledIfEnvironmentVariable(named="FITCORE_TENANCY_TEST_TOKEN",matches="[a-f0-9]{64}")
class CatalogSchemaCertificationTest {
    final JsonMapper json=JsonMapper.builder().build();
    Map<String,Object> capture(TenantRecoveryTest db)throws Exception{
        try(var c=db.restoredConnection();var s=c.createStatement();var result=s.executeQuery("select string_agg(version,',' order by installed_rank),bool_and(success) from flyway_schema_history")){
            result.next();assertThat(result.getString(1)).isEqualTo("1,2,4,5,6,7,8,9");assertThat(result.getBoolean(2)).isTrue();
            assertThat(c.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
            return Map.of("hash",SchemaContract.fingerprint(c),"descriptor",SchemaDescriptorSnapshot.collect(c),"engine",c.getMetaData().getDatabaseProductVersion());
        }
    }
    @Test void completeV9FreshStagedAndRestoredDescriptorsAreExactlyCertified()throws Exception{
        var evidence=new LinkedHashMap<String,Object>();var clean=new CleanInstanceRecoveryTest();String saved=null;
        try{
            clean.prepare();clean.seed();evidence.put("fresh",capture(clean.source));
            clean.restored.freshTarget();clean.restored.restore(clean.dump());saved=clean.restored.fixture.schema;clean.restored.fixture.schema=clean.source.fixture.schema;
            evidence.put("fullV9Restore",capture(clean.restored));
        }finally{if(saved!=null)clean.restored.fixture.schema=saved;clean.cleanup();}
        for(int stage:List.of(2,4,5,6,8)){
            var db=new TenantRecoveryTest();
            try{
                db.prepare();db.fixture.legacyRecords();
                if(stage>=4){try(var c=db.fixture.connection()){c.setAutoCommit(false);db.fixture.stageExplicitSyntheticMapping(c,false);c.commit();}db.fixture.migrate(Integer.toString(stage));}
                Path archive=db.backup();db.freshTarget();db.restore(archive);
                if(stage==2)try(var c=db.restoredConnection()){c.setAutoCommit(false);db.fixture.stageExplicitSyntheticMapping(c,false);c.commit();}
                Flyway.configure().dataSource(db.targetUrl,TenantRecoveryTest.USER,TenantRecoveryTest.PASSWORD).defaultSchema(db.fixture.schema).schemas(db.fixture.schema).baselineOnMigrate(false).cleanDisabled(true).load().migrate();
                evidence.put("archiveV"+stage+"ThenV9",capture(db));
            }finally{db.cleanup();}
        }
        Path file=Path.of("target","catalog-schema-certification.json");Files.createDirectories(file.getParent());Files.writeString(file,json.writeValueAsString(evidence));
        var comparison=new SchemaReadinessCertificationTest();
        Map<String,String> fresh=(Map<String,String>)((Map<?,?>)evidence.get("fresh")).get("descriptor");
        for(var entry:evidence.entrySet()){
            Map<String,String> rows=(Map<String,String>)((Map<?,?>)entry.getValue()).get("descriptor");assertThat(rows.keySet()).isEqualTo(fresh.keySet());
            for(String key:rows.keySet())assertThat(comparison.equivalentRendering(key,rows.get(key))).as("Only reviewed parser renderings differ: %s / %s",entry.getKey(),key).isEqualTo(comparison.equivalentRendering(key,fresh.get(key)));
        }
        for(Object entry:evidence.values())assertThat(SchemaContract.ACCEPTED).contains((String)((Map<?,?>)entry).get("hash"));
    }
}
