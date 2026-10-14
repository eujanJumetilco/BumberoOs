package com.groupnine.bumberoos.dto;

import jakarta.validation.Valid;

import java.util.List;

/** Optional body of the resolve call. */
public record ResolveRequest(
        List<@Valid ItemReturn> itemReturns
) {}
