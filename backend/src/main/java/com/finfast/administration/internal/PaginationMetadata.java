package com.finfast.administration.internal;

public record PaginationMetadata(
        long total,
        int page,
        int limit,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {}

