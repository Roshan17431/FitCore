package com.fitcore.gym.config;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EmailDeliverySafetyTest {
    @Test void configurableProviderExistsAndDefaultsToDisabled() throws Exception {
        Class<?> config=Class.forName("com.fitcore.gym.config.EmailDeliveryConfiguration");
        new ApplicationContextRunner().withUserConfiguration(config).run(c->{
            Object sender=c.getBean("transactionalEmailSender");
            assertThatThrownBy(()->sender.getClass().getMethod("send",String.class,String.class,String.class).invoke(sender,"synthetic@fixture.invalid","Subject","Secret body"))
                .hasRootCauseMessage("Email delivery is disabled");
        });
    }
    @Test void enablingProviderRequiresSeparateImplementationAndApproval() throws Exception {
        Class<?> config=Class.forName("com.fitcore.gym.config.EmailDeliveryConfiguration");
        new ApplicationContextRunner().withUserConfiguration(config).withPropertyValues("fitcore.email.provider=smtp").run(c->assertThat(c).hasFailed());
    }
}
