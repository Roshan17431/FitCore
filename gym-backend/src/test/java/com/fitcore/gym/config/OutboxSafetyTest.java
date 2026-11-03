package com.fitcore.gym.config;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
class OutboxSafetyTest {
 @Test void disabledOutboxCannotHaveAnActiveWorker(){new ApplicationContextRunner().withUserConfiguration(OutboxConfiguration.class).withPropertyValues("fitcore.email.outbox.worker-enabled=true").run(c->assertThat(c).hasFailed());}
 @Test void enabledOutboxRequiresExternalKeyRing(){new ApplicationContextRunner().withUserConfiguration(OutboxConfiguration.class).withPropertyValues("fitcore.email.outbox.enabled=true").run(c->assertThat(c.getStartupFailure()).hasStackTraceContaining("external outbox key ring"));}
 @Test void defaultProviderCannotSend(){new ApplicationContextRunner().withUserConfiguration(OutboxConfiguration.class).run(c->assertThatThrownBy(()->c.getBean(com.fitcore.gym.email.EmailProvider.class).accept(java.util.UUID.randomUUID(),new com.fitcore.gym.email.OutboxCipher.Message("fake@fixture.invalid","s","t","h"))).hasMessage("External email delivery is disabled"));}
}
