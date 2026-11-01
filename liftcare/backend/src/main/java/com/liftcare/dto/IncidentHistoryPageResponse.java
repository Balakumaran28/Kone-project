package com.liftcare.dto;

import java.util.List;

public record IncidentHistoryPageResponse(
        List<IncidentHistoryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {}
