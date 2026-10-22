package com.finfast.administration.internal;

import java.util.List;

public record PaginatedResponse<T>(
        List<T> items,
        PaginationMetadata pagination
) {}

