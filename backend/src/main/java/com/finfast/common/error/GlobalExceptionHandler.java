package com.finfast.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.stream.Stream;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final MediaType JSON_SUFFIX = new MediaType("application", "*+json");

    private final ContentNegotiationManager contentNegotiation;

    public GlobalExceptionHandler(ContentNegotiationManager contentNegotiation) {
        this.contentNegotiation = contentNegotiation;
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, QueryTimeoutException.class,
            CannotCreateTransactionException.class})
    ResponseEntity<Object> handleInfrastructureFailure(Exception exception, WebRequest request) {
        return handleExceptionInternal(exception, null, new HttpHeaders(), HttpStatus.SERVICE_UNAVAILABLE, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpectedFailure(Exception exception, WebRequest request) {
        return handleExceptionInternal(exception, null, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
                                                            HttpStatusCode status, WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest && servletRequest.getResponse() != null
                && servletRequest.getResponse().isCommitted()) {
            return null;
        }
        if (status.value() == 406) {
            // There is no acceptable representation; retain negotiation headers without
            // causing another converter exception while trying to serialize an error body.
            return new ResponseEntity<>(null, headers, status);
        }
        ErrorResponse response = responseFor(status);
        if (status.is5xxServerError()) {
            LOG.atError()
                    .addKeyValue("event.action", "http.request.failed")
                    .addKeyValue("event.outcome", "failure")
                    .addKeyValue("error.code", response.code())
                    .setCause(exception)
                    .log("HTTP request failed");
        }
        // Keep Spring's status, headers, committed-response and async handling semantics.
        return super.handleExceptionInternal(exception, response, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode status,
                                                           WebRequest request) {
        // Keep the original failure status/headers when its JSON body cannot be negotiated.
        // Returning an empty body avoids a second exception from the error handler itself.
        if (body != null && !acceptsJson(request)) {
            body = null;
        }
        return super.createResponseEntity(body, headers, status, request);
    }

    private boolean acceptsJson(WebRequest request) {
        if (!(request instanceof ServletWebRequest servletRequest)) {
            return false;
        }
        try {
            List<MediaType> accepted = contentNegotiation.resolveMediaTypes(servletRequest);
            // Check JSON and explicitly requested vendor JSON representations. An
            // explicit q=0 must take precedence over a less-specific positive wildcard.
            return Stream.concat(Stream.of(MediaType.APPLICATION_JSON), accepted.stream()
                            .filter(type -> type.isConcrete() && type.isCompatibleWith(JSON_SUFFIX)))
                    .anyMatch(candidate -> acceptsRepresentation(accepted, candidate));
        } catch (HttpMediaTypeNotAcceptableException exception) {
            return false;
        }
    }

    private static boolean acceptsRepresentation(List<MediaType> accepted, MediaType candidate) {
        MediaType mostSpecific = null;
        for (MediaType range : accepted) {
            if (range.includes(candidate) && (mostSpecific == null
                    || range.removeQualityValue().isMoreSpecific(mostSpecific.removeQualityValue()))) {
                mostSpecific = range;
            }
        }
        return mostSpecific != null && mostSpecific.getQualityValue() > 0;
    }

    private static ErrorResponse responseFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> new ErrorResponse("INVALID_REQUEST", "The request is invalid.");
            case 404 -> new ErrorResponse("NOT_FOUND", "The requested resource was not found.");
            case 405 -> new ErrorResponse("METHOD_NOT_ALLOWED", "The request method is not supported.");
            case 413 -> new ErrorResponse("PAYLOAD_TOO_LARGE", "The request is too large.");
            case 415 -> new ErrorResponse("UNSUPPORTED_MEDIA_TYPE", "The request format is not supported.");
            case 500 -> new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred.");
            case 503 -> new ErrorResponse("SERVICE_UNAVAILABLE", "The service is temporarily unavailable.");
            default -> status.is5xxServerError()
                    ? new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred.")
                    : new ErrorResponse("HTTP_" + status.value(), "The request could not be completed.");
        };
    }
}
