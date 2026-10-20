package com.finfast.common.error;

// Generic HTTP errors carry no assertion about a durable financial outcome.
public record ErrorResponse(String code, String message) {
}
