package com.aiimpacteval.apicore.metrics;

import com.aiimpacteval.apicore.metrics.CockpitDtos.CockpitResponse;
import com.aiimpacteval.apicore.metrics.CockpitDtos.CockpitTile;
import com.aiimpacteval.apicore.metrics.CockpitDtos.DailyValue;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads pre-aggregated tiles from {@code mart.metric_daily}. Tile metadata (label, unit,
 * aggregation semantics, plain-language definition) lives here so the frontend never invents
 * metric semantics — definitions are a product requirement (PRD §9 "definitions on demand").
 */
@Service
public class CockpitQueryService {

    private record TileSpec(String metricKey, String label, String unit, String aggregation, String definition) {
    }

    private static final List<TileSpec> TILE_SPECS = List.of(
            new TileSpec("deployment_frequency", "Deployment frequency", "deploys", "sum",
                    "Number of successful production deployments in the window, detected from CI "
                            + "workflow runs matching the repo's deployment rule. No manual tagging."),
            new TileSpec("lead_time_p50_hours", "Lead time for changes", "hours (median)", "latest",
                    "Median time from a pull request opening to the first successful production "
                            + "deployment after it merges (v1 heuristic linkage). Latest day shown."),
            new TileSpec("change_failure_rate", "Change failure rate", "%", "rate",
                    "Share of production deployments followed within 48h by a hotfix or rollback "
                            + "deployment on the same repository."),
            new TileSpec("mttr_p50_hours", "Time to restore", "hours (median)", "latest",
                    "Median time from a failed deployment to its remediating hotfix/rollback. "
                            + "Latest day shown."),
            new TileSpec("pr_velocity", "PR velocity", "merged PRs", "sum",
                    "Pull requests merged in the window."),
            new TileSpec("pr_cycle_time_p50_hours", "PR cycle time", "hours (median)", "latest",
                    "Median time from pull request opened to merged, per day. Latest day shown."));

    private final JdbcTemplate jdbcTemplate;

    public CockpitQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @param scope a repo full name, {@code "*"} for the org rollup, or a team UUID (E4-S2) —
     *              opaque to this method, matched against {@code mart.metric_daily.scope_id}.
     */
    public CockpitResponse cockpit(int windowDays, String scope) {
        List<CockpitTile> tiles = new ArrayList<>();
        Instant asOf = null;

        for (TileSpec spec : TILE_SPECS) {
            List<DailyValue> series = new ArrayList<>();
            var holder = new Object() {
                int sampleSize = 0;
                BigDecimal weightedNumerator = BigDecimal.ZERO;
                Instant latestComputedAt = null;
            };
            jdbcTemplate.query("""
                            SELECT day, value, sample_size, computed_at
                            FROM mart.metric_daily
                            WHERE metric_key = ? AND scope_id = ? AND day >= (now() AT TIME ZONE 'UTC')::date - ?
                            ORDER BY day
                            """,
                    rs -> {
                        BigDecimal value = rs.getBigDecimal("value");
                        int sample = rs.getInt("sample_size");
                        series.add(new DailyValue(rs.getDate("day").toLocalDate(), value));
                        holder.sampleSize += sample;
                        holder.weightedNumerator = holder.weightedNumerator.add(value.multiply(BigDecimal.valueOf(sample)));
                        Timestamp computedAt = rs.getTimestamp("computed_at");
                        if (holder.latestComputedAt == null || computedAt.toInstant().isAfter(holder.latestComputedAt)) {
                            holder.latestComputedAt = computedAt.toInstant();
                        }
                    },
                    spec.metricKey(), scope, windowDays);

            BigDecimal aggregate = switch (spec.aggregation()) {
                case "sum" -> series.isEmpty() ? null
                        : series.stream().map(DailyValue::value).reduce(BigDecimal.ZERO, BigDecimal::add);
                // Sample-weighted average — for ratios like CFR, sum(numerator)/sum(denominator).
                case "rate" -> holder.sampleSize == 0 ? null
                        : holder.weightedNumerator.divide(BigDecimal.valueOf(holder.sampleSize), 4, RoundingMode.HALF_UP);
                default -> series.isEmpty() ? null : series.get(series.size() - 1).value();
            };
            if (holder.latestComputedAt != null && (asOf == null || holder.latestComputedAt.isAfter(asOf))) {
                asOf = holder.latestComputedAt;
            }
            tiles.add(new CockpitTile(spec.metricKey(), spec.label(), spec.unit(), spec.aggregation(),
                    aggregate, holder.sampleSize, spec.definition(), series));
        }
        return new CockpitResponse(asOf, windowDays, scope, tiles);
    }
}
