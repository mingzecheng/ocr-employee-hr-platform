package com.hrplatform.common.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerLogMaskingTest {

    private Logger handlerLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        handlerLogger.detachAppender(appender);
    }

    @Test
    void unexpectedExceptionLogsMaskedPiiInsteadOfRawMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        RuntimeException piiException = new RuntimeException(
                "员工张伟手机号13812345678身份证110101199001011234档案读取失败");

        handler.unexpected(piiException, new MockHttpServletRequest());

        String output = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", String::concat);

        assertThat(output).doesNotContain("13812345678");
        assertThat(output).contains("138****5678");
        assertThat(output).doesNotContain("110101199001011234");
        assertThat(output).contains("1101**********1234");
    }
}
