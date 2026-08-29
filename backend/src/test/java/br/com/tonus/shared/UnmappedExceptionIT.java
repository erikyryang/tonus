package br.com.tonus.shared;

import br.com.tonus.auth.AbstractAuthIntegrationTest;
import br.com.tonus.shared.internal.web.GlobalExceptionHandler;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-001 acceptance criterion: an error never turns into a stacktrace in
 * the response.
 *
 * ProblemDetailsHandlerIT covers the 4xx cases, which Spring converts on
 * its own — the global handler doesn't even participate in that path. What
 * is verified here is the reason it exists: an exception nobody mapped
 * turns into a 500 in Problem Details, without leaking the class, internal
 * message, or stacktrace.
 */
class UnmappedExceptionIT extends AbstractAuthIntegrationTest {

    @Test
    void unmappedExceptionBecomesProblemDetailsWithoutLeakingInternals() throws Exception {
        Cookie admin = adminCookie("admin-boom@tonus.com");

        mockMvc.perform(get("/test/probe/boom").cookie(admin))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("Erro interno inesperado"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("segredo interno"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("IllegalStateException"))));
    }

    /**
     * The client sees a generic 500, but the cause must be findable: a 500
     * that is never logged is a bug that hides itself. The handler has to
     * record the exception server-side with its stacktrace.
     */
    @Test
    void unmappedExceptionIsLoggedServerSide() throws Exception {
        Cookie admin = adminCookie("admin-boom-log@tonus.com");
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            mockMvc.perform(get("/test/probe/boom").cookie(admin))
                    .andExpect(status().isInternalServerError());

            assertThat(appender.list)
                    .anyMatch(event -> event.getLevel() == Level.ERROR
                            && event.getThrowableProxy() != null
                            && event.getThrowableProxy().getClassName().contains("IllegalStateException"));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
