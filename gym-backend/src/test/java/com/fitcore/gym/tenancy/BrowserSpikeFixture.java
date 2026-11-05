package com.fitcore.gym.tenancy;

import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Command-line fixture for the owned disposable HTTPS browser harness, never application startup. */
public final class BrowserSpikeFixture {
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new IllegalArgumentException("No database/ownership arguments accepted");
        var f=new FoundationMigrationTest();f.schema="public";
        // connection verifies the harness URL, exact database name and real container marker.
        try(var ignored=f.connection()){}f.migrate("latest");
        String hash=new BCryptPasswordEncoder().encode("Browser-Spike!2026");
        try(var c=f.connection()){
            c.setAutoCommit(false);
            for(int n=1;n<=2;n++){
                f.owner(c,n,"OWNER",true);
                FoundationMigrationTest.execute(c,"update users set password_hash=?,email_verified_at=now() where id=?",hash,f.user(n));
                for(int role=0;role<3;role++){
                    String name=new String[]{"ADMIN","TRAINER","MEMBER"}[role];
                    FoundationMigrationTest.execute(c,"insert into users(id,full_name,email,password_hash,role,enabled,email_verified_at,gym_id,created_at,updated_at) values(?,?,?,?,?,true,now(),?,now(),now())",user(n,role),"GYM_"+n+"_"+name,name.toLowerCase()+n+"@fixture.invalid",hash,name,f.gym(n));
                }
                FoundationMigrationTest.execute(c,"insert into membership_plans(id,name,price,duration_months,recommended,gym_id) values(?,'Basic',29,1,false,?),(?,'Premium',79,3,true,?)",n*100,f.gym(n),n*100+1,f.gym(n));
                FoundationMigrationTest.execute(c,"insert into trainer_profiles(id,user_id,specialization,experience_years,gym_id) values(?,?,'Strength',4,?)",n*100,user(n,1),f.gym(n));
                FoundationMigrationTest.execute(c,"insert into exercises(id,name,category,difficulty,gym_id) values(?,'Historical Bench','Strength','BEGINNER',?)",n*100,f.gym(n));
                FoundationMigrationTest.execute(c,"insert into attendance(user_id,gym_id,check_in_date,check_in_time) values(?,?,current_date,now())",user(n,2),f.gym(n));
            }
            for(String table:new String[]{"membership_plans","trainer_profiles","exercises"})FoundationMigrationTest.execute(c,"select setval(pg_get_serial_sequence('"+table+"','id'),(select max(id) from "+table+"))");
            c.commit();
        }
        f.sql("create role browser_spike_runtime login password 'synthetic-browser-runtime-only' nosuperuser nocreatedb nocreaterole nobypassrls noinherit");
        f.sql("grant usage on schema public to browser_spike_runtime");
        f.sql("grant select,insert,update,delete on all tables in schema public to browser_spike_runtime");
        f.sql("revoke all on flyway_schema_history from browser_spike_runtime");
        f.sql("grant select on flyway_schema_history to browser_spike_runtime");
        f.sql("grant usage,select on all sequences in schema public to browser_spike_runtime");
    }
    static UUID user(int gym,int role){return UUID.fromString("30000000-0000-0000-0000-"+String.format("%012d",gym*10+role));}
}
