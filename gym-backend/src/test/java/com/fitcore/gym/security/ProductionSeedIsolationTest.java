package com.fitcore.gym.security;

import com.fitcore.gym.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

// Legacy hosting environments may still request SQL initialization. They must not
// discover the demo script at Spring Boot's automatic data.sql location.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:production-seed-isolation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
class ProductionSeedIsolationTest {
    @Autowired UserRepository users;

    @Test
    void ordinaryStartupDoesNotCreatePublicDemoAccounts() {
        assertThat(users.findAll()).isEmpty();
    }
}
