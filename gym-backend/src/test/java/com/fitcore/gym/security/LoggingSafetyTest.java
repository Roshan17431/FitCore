package com.fitcore.gym.security;

import static org.assertj.core.api.Assertions.*;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import com.fitcore.gym.exception.GlobalExceptionHandler;

class LoggingSafetyTest {
    @Test void unexpectedErrorsDoNotLogExceptionPayloads() {
        Logger logger=(Logger)LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var capture=new ListAppender<ILoggingEvent>();capture.start();logger.addAppender(capture);
        try {
            var response=new GlobalExceptionHandler().handleAllExceptions(new RuntimeException("sensitive-synthetic-payload"),new MockHttpServletRequest("GET","/api/gym"));
            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(capture.list).isNotEmpty();
            assertThat(capture.list).allSatisfy(event->{assertThat(event.getFormattedMessage()).doesNotContain("sensitive-synthetic-payload");assertThat(event.getThrowableProxy()).isNull();});
        } finally {logger.detachAppender(capture);capture.stop();}
    }
}
