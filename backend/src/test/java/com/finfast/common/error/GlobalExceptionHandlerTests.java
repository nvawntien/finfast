package com.finfast.common.error;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.sql.SQLException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GlobalExceptionHandlerTests.FixtureController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTests.FixtureController.class})
class GlobalExceptionHandlerTests {

    @Autowired
    private MockMvc mvc;

    private Logger logger;
    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void captureBoundaryEvents() {
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        events = new ListAppender<>();
        events.setContext(logger.getLoggerContext());
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void detachCapture() {
        logger.detachAppender(events);
        events.stop();
    }

    @Test
    void unexpectedFailureIsSafeLoggedOnceAndPreservesOriginalCause() throws Exception {
        var result = mvc.perform(get("/test-only/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("""
                        {"code":"INTERNAL_ERROR","message":"An unexpected error occurred."}
                        """, JsonCompareMode.STRICT))
                .andExpect(jsonPath("$.outcome").doesNotExist()).andReturn();
        assertThat(events.list).hasSize(1);
        ILoggingEvent event = events.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).isEqualTo("HTTP request failed");
        assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key + "=" + pair.value)
                .containsExactly("event.action=http.request.failed", "event.outcome=failure", "error.code=INTERNAL_ERROR");
        Throwable original = ((ThrowableProxy) event.getThrowableProxy()).getThrowable();
        assertThat(original).isSameAs(result.getResolvedException()).hasCauseInstanceOf(SQLException.class);
        assertThat(original.getCause().getMessage()).contains("synthetic-secret");
        assertThat(original.getStackTrace()).isNotEmpty();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("SQLException", "IllegalStateException", "SELECT",
                "synthetic-secret", "/internal/path", "stackTrace", "REJECTED", "UNKNOWN", "PROCESSING");
    }

    @ParameterizedTest
    @ValueSource(strings = {"database", "timeout", "transaction"})
    void infrastructureFailuresAreNonfinal503Responses(String kind) throws Exception {
        mvc.perform(get("/test-only/infrastructure").param("kind", kind))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("""
                        {"code":"SERVICE_UNAVAILABLE","message":"The service is temporarily unavailable."}
                        """, JsonCompareMode.STRICT));
        assertThat(events.list).hasSize(1);
        assertThat(events.list.getFirst().getLevel()).isEqualTo(Level.ERROR);
        assertThat(events.list.getFirst().getThrowableProxy().getCause()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("clientFailures")
    void protocolAndValidationFailuresRetainHttpSemanticsWithoutErrorLogs(
            MockHttpServletRequestBuilder request, int expectedStatus, String code) throws Exception {
        var result = mvc.perform(request).andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.outcome").doesNotExist()).andReturn();
        assertThat(events.list).noneMatch(event -> event.getLevel() == Level.ERROR);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private-value", "Exception", "stackTrace", "rejectedValue");
    }

    private static Stream<Arguments> clientFailures() {
        return Stream.of(
                Arguments.of(post("/test-only/input").contentType(MediaType.APPLICATION_JSON).content("{\"value\":"), 400, "INVALID_REQUEST"),
                Arguments.of(post("/test-only/input").contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"\"}"), 400, "INVALID_REQUEST"),
                Arguments.of(post("/test-only/input").contentType(MediaType.APPLICATION_JSON), 400, "INVALID_REQUEST"),
                Arguments.of(get("/test-only/parameter"), 400, "INVALID_REQUEST"),
                Arguments.of(get("/test-only/parameter").param("size", "private-value"), 400, "INVALID_REQUEST"),
                Arguments.of(get("/test-only/parameter").param("size", "0"), 400, "INVALID_REQUEST"),
                Arguments.of(post("/test-only/input").contentType(MediaType.TEXT_PLAIN).content("private-value"), 415, "UNSUPPORTED_MEDIA_TYPE"),
                Arguments.of(get("/test-only/not-found"), 404, "NOT_FOUND"),
                Arguments.of(get("/test-only/safe-status"), 404, "NOT_FOUND"));
    }

    @Test
    void unacceptableRepresentationRetains406WithoutFailingToSerializeAnotherBody() throws Exception {
        mvc.perform(get("/test-only/json").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable()).andExpect(header().string("Accept", "application/json"))
                .andExpect(content().string(""));
        assertThat(events.list).isEmpty();
    }

    @Test
    void malformedAcceptHeaderRetainsSafe406() throws Exception {
        mvc.perform(get("/test-only/json").header("Accept", "not-a-media-type"))
                .andExpect(status().isNotAcceptable()).andExpect(content().string(""));
        assertThat(events.list).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/xml", "application/json;q=0"})
    void unexpectedFailureWithUnacceptableRepresentationDoesNotLeakOrReportTwice(String accept) throws Exception {
        mvc.perform(get("/test-only/unexpected").header("Accept", accept))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(""));
        assertThat(events.list).hasSize(1);
    }

    @Test
    void validationFailureWithUnacceptableRepresentationRemains400WithoutErrorLog() throws Exception {
        mvc.perform(get("/test-only/parameter").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isBadRequest()).andExpect(content().string(""));
        assertThat(events.list).isEmpty();
    }

    @Test
    void infrastructureFailureWithUnacceptableRepresentationRemains503WithoutFinalOutcome() throws Exception {
        mvc.perform(get("/test-only/infrastructure").param("kind", "database").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isServiceUnavailable()).andExpect(content().string(""));
        assertThat(events.list).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "application/vnd.finfast+json", "*/*"})
    void acceptableJsonErrorsRetainTheSafeContract(String accept) throws Exception {
        mvc.perform(get("/test-only/unexpected").header("Accept", accept))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("""
                        {"code":"INTERNAL_ERROR","message":"An unexpected error occurred."}
                        """, JsonCompareMode.STRICT));
        assertThat(events.list).hasSize(1);
    }

    @Test
    void frameworkServerErrorPreservesHeadersAndOriginalCause() throws Exception {
        var failure = new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE);
        failure.getHeaders().set(HttpHeaders.RETRY_AFTER, "15");
        var response = handler().handleException(failure,
                new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("15");
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("SERVICE_UNAVAILABLE", "The service is temporarily unavailable."));
        assertThat(events.list).hasSize(1);
        assertThat(((ThrowableProxy) events.list.getFirst().getThrowableProxy()).getThrowable()).isSameAs(failure);
    }

    @Test
    void unusableAsyncResponseRetainsFrameworkNoWriteBehavior() throws Exception {
        var response = handler().handleException(new AsyncRequestNotUsableException("private-value"),
                new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()));
        assertThat(response).isNull();
        assertThat(events.list).isEmpty();
    }

    @Test
    void methodNotAllowedPreservesAllowHeader() throws Exception {
        mvc.perform(post("/test-only/unexpected"))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        assertThat(events.list).isEmpty();
    }

    @Test
    void committedResponseIsNotRewrittenOrReportedAgain() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);
        var request = new ServletWebRequest(new MockHttpServletRequest(), response);
        assertThat(handler().handleUnexpectedFailure(new IllegalStateException("private"), request)).isNull();
        assertThat(events.list).isEmpty();
    }

    private static GlobalExceptionHandler handler() {
        return new GlobalExceptionHandler(new ContentNegotiationManager());
    }

    // Compiled only into test classes. No diagnostic endpoints are shipped in the executable JAR.
    @RestController
    static class FixtureController {

        @GetMapping("/test-only/unexpected")
        void unexpected() {
            throw new IllegalStateException("private /internal/path", new SQLException("SELECT password_hash; synthetic-secret"));
        }

        @GetMapping("/test-only/infrastructure")
        void infrastructure(@RequestParam String kind) {
            SQLException cause = new SQLException("private database credentials");
            throw switch (kind) {
                case "database" -> new DataAccessResourceFailureException("private database message", cause);
                case "timeout" -> new QueryTimeoutException("private timeout message", cause);
                default -> new CannotCreateTransactionException("private transaction message", cause);
            };
        }

        @PostMapping(value = "/test-only/input", consumes = MediaType.APPLICATION_JSON_VALUE)
        void input(@Valid @RequestBody Input input) {
        }

        @GetMapping("/test-only/parameter")
        void parameter(@RequestParam @Min(1) int size) {
        }

        @GetMapping("/test-only/safe-status")
        void safeStatus() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "private-value");
        }

        @GetMapping(value = "/test-only/json", produces = MediaType.APPLICATION_JSON_VALUE)
        Input json() {
            return new Input("valid");
        }
    }

    record Input(@NotBlank String value) {
    }
}
