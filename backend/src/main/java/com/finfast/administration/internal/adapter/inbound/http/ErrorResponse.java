package com.finfast.administration.internal.adapter.inbound.http;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, String outcome) {
    public ErrorResponse {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    public ErrorResponse(String code, String message) {
        this(code, message, null);
    }
}

