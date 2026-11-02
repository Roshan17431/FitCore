package com.fitcore.gym.config;

import com.zaxxer.hikari.HikariConfig;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.sql.DriverManager;
import java.util.Objects;

/** Deliberately removed/replaced only when backend tenant isolation is complete. */
@Configuration
public class FoundationSafetyConfiguration {
    @Bean
    static BeanFactoryPostProcessor requireDisposableFoundationDatabase(Environment env) {
        return factory -> {
            if (env.getProperty("aws.s3.enabled", Boolean.class, false))
                throw new IllegalStateException("Tenant assets are not implemented");
            if(!"none".equals(env.getProperty("server.forward-headers-strategy","none")))
                throw new IllegalStateException("Forwarded client identity needs an approved proxy policy");
            rejectAlternativeConnections(env);
            String url = env.getProperty("spring.datasource.url", "");
            // H2 is test-scoped and never bundled in the runtime JAR. No INIT scripts/URLs.
            if (url.matches(
                    "jdbc:h2:mem:[A-Za-z0-9_-]+(?:;(?:MODE=PostgreSQL|DB_CLOSE_DELAY=-1))*")) {
                return;
            }
            String token = System.getenv("FITCORE_TENANCY_TEST_TOKEN");
            if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/fitcore_phase2(?:_e2e)?")
                    || token == null
                    || !token.matches("[a-f0-9]{64}")) {
                throw new IllegalStateException(
                        "Foundation build requires an owned disposable database; tenant HTTP"
                            + " isolation is incomplete");
            }
            try (var c =
                            DriverManager.getConnection(
                                    url,
                                    env.getProperty("spring.datasource.username"),
                                    env.getProperty("spring.datasource.password"));
                    var s = c.createStatement();
                    var r =
                            s.executeQuery(
                                    "select shobj_description(oid,'pg_database') from pg_database"
                                        + " where datname=current_database()")) {
                if (!r.next() || !Objects.equals(r.getString(1), "fitcore-disposable:" + token)) {
                    throw new IllegalStateException(
                            "Foundation build requires an owned disposable database: marker"
                                + " mismatch");
                }
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("Cannot verify disposable foundation database", e);
            }
        };
    }

    private static void rejectAlternativeConnections(Environment env) {
        try {
            // Use Boot's relaxed binding, including environment-variable aliases, before
            // Hikari/Flyway can initialize any connection. Only the checked root URL is supported.
            Binder binder = Binder.get(env);
            HikariConfig hikari =
                    binder.bind("spring.datasource.hikari", HikariConfig.class)
                            .orElseGet(HikariConfig::new);
            if (hikari.getJdbcUrl() != null
                    || hikari.getDataSourceClassName() != null
                    || hikari.getDataSourceJNDI() != null
                    || hikari.getDataSource() != null
                    || hikari.getDriverClassName() != null
                    || !hikari.getDataSourceProperties().isEmpty()
                    || env.containsProperty("spring.datasource.jndi-name")
                    || env.containsProperty("spring.datasource.type")
                    || env.containsProperty("spring.flyway.url")
                    || env.containsProperty("spring.flyway.driver-class-name")
                    || binder.bind(
                                    "spring.flyway.jdbc-properties",
                                    Bindable.mapOf(String.class, String.class))
                            .isBound()) {
                throw new IllegalStateException("Unsupported database selector");
            }
        } catch (RuntimeException unsupported) {
            throw new IllegalStateException(
                    "Alternative database connection settings are forbidden in foundation builds",
                    unsupported);
        }
    }
}
