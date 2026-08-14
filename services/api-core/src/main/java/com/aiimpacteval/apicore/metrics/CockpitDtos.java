package com.aiimpacteval.apicore.metrics;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Response DTOs for GET /api/v1/metrics/cockpit — mirror openapi/api-core.yml. */
public final class CockpitDtos {

    public record CockpitResponse(Instant asOf, int windowDays, String scope, List<CockpitTile> tiles) {
    }

    public record CockpitTile(String metricKey, String label, String unit, String aggregation,
                              BigDecimal aggregate, int sampleSize, String definition,
                              List<DailyValue> series) {
    }

    public record DailyValue(LocalDate day, BigDecimal value) {
    }

    private CockpitDtos() {
    }
}
