package com.fitcore.gym.tenancy;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.nio.file.*;

class TenantRepositoryArchitectureTest {
    @Test
    void productionRepositoriesDoNotExposeGlobalBusinessReads() throws Exception {
        try (var files = Files.list(Path.of("src/main/java/com/fitcore/gym/repository"))) {
            for (var file : files.filter(p -> p.toString().endsWith("Repository.java")).toList()) {
                String name = file.getFileName().toString();
                String source = Files.readString(file);
                assertThat(source)
                        .as(name)
                        .doesNotContain("extends JpaRepository", "deleteAll(", "findAll(");
                if (!java.util.Set.of("UserIdentityRepository.java", "GymRepository.java")
                        .contains(name))
                    assertThat(source)
                            .as(name)
                            .doesNotContain("findById(", "findByEmail(", "existsByEmail(");
            }
        }
    }

    @Test
    void unownedAssetsCannotBeActivated() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(com.fitcore.gym.config.FoundationSafetyConfiguration.class)
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:safety", "aws.s3.enabled=true")
                .run(
                        c ->
                                assertThat(c.getStartupFailure())
                                        .hasStackTraceContaining(
                                                "Tenant assets are not implemented"));
    }

    @Test
    void globalIdentityLookupIsConfinedToApprovedAuthenticationClasses() throws Exception {
        var base = Path.of("src/main/java/com/fitcore/gym");
        try (var files = Files.walk(base)) {
            for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (file.toString().contains("/repository/")) continue;
                if (Files.readString(file).contains("UserIdentityRepository"))
                    assertThat(file.getFileName().toString())
                            .isIn(
                                    "AuthService.java", "AccountSecurityService.java",
                                    "CustomUserDetailsService.java",
                                    "IdentityUniqueness.java");
            }
        }
    }
}
