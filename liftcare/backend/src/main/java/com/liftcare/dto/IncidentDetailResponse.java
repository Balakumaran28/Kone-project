package com.liftcare.dto;

import java.util.List;

public record IncidentDetailResponse(
        IncidentResponse incident,
        List<IncidentHistoryResponse> history
) {}
