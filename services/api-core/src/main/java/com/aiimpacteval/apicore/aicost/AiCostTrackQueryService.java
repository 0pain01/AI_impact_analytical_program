package com.aiimpacteval.apicore.aicost;

import com.aiimpacteval.apicore.aicost.AiCostTrackDtos.AiCostTrackResponse;
import com.aiimpacteval.apicore.aicost.AiCostTrackDtos.Assumptions;
import com.aiimpacteval.apicore.aicost.AiCostTrackDtos.DailySpendPoint;
import com.aiimpacteval.apicore.aicost.AiCostTrackDtos.DeveloperAllocation;
import com.aiimpacteval.apicore.aicost.AiCostTrackDtos.ImpactMetrics;
import com.aiimpacteval.apicore.aicost.AiCostTrackDtos.Kpis;
import com.aiimpacteval.apicore.aicost.AiCostTrackDtos.RoiMetrics;
import com.aiimpacteval.apicore.aicost.AiCostTrackDtos.SpendByTool;
import com.aiimpacteval.apicore.aicost.AiCostTrackDtos.SpendTrendPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI Cost Track (PRD E9, AI-01..AI-05) computed from {@code staging.ai_usage_state} (Claude
 * Code + Copilot usage, {@code connector-ai-telemetry}) and {@code staging.pull_request_state}
 * (AI-04/AI-05's cycle-time segmentation, via its {@code ai_assisted} flag — V13 migration).
 * All figures here are ingested-data-plus-real-formula, not hardcoded — but the input data
 * itself is sample data shaped like each vendor's real usage-report/PR-payload schema, not a
 * genuine enterprise export; see the connector README and CHANGELOG for the swap-in-real-data
 * seam. See {@link AiCostTrackDtos.AiCostTrackResponse}'s javadoc for exactly what's null when
 * the underlying sample is too small (team allocation, impact, roi) — never silently blended
 * into a fabricated number.
 */
@Service
public class AiCostTrackQueryService {

    private static final String TOOL_LABEL_CLAUDE = "Claude Code";
    private static final String TOOL_LABEL_COPILOT = "GitHub Copilot";

    // Below this many merged PRs in a bucket, a median is too noisy to report — AI-04/AI-05 show
    // "not available yet" rather than a headline number computed from 1-2 samples.
    private static final int MIN_PRS_PER_BUCKET = 3;

    private final JdbcTemplate jdbcTemplate;
    private final BigDecimal copilotMonthlySeatCostUsd;
    private final int licensedSeatsAssumed;
    private final BigDecimal blendedHourlyRateUsd;

    public AiCostTrackQueryService(JdbcTemplate jdbcTemplate,
                                   @Value("${ai-cost.copilot.monthly-seat-cost-usd}") BigDecimal copilotMonthlySeatCostUsd,
                                   @Value("${ai-cost.licensed-seats}") int licensedSeatsAssumed,
                                   @Value("${ai-cost.blended-hourly-rate-usd}") BigDecimal blendedHourlyRateUsd) {
        this.jdbcTemplate = jdbcTemplate;
        this.copilotMonthlySeatCostUsd = copilotMonthlySeatCostUsd;
        this.licensedSeatsAssumed = licensedSeatsAssumed;
        this.blendedHourlyRateUsd = blendedHourlyRateUsd;
    }

    public AiCostTrackResponse aiCostTrack(int windowDays) {
        LocalDate windowStart = LocalDate.now().minusDays(windowDays);
        Date since = Date.valueOf(windowStart);

        Kpis kpis = queryKpis(since);
        List<SpendByTool> spendByTool = querySpendByTool(since);
        List<SpendTrendPoint> spendTrend = querySpendTrend(since);
        List<DailySpendPoint> dailySpend = queryDailySpend(since);
        List<DeveloperAllocation> developerAllocation = queryDeveloperAllocation(since);
        ImpactMetrics impact = queryImpact(since);
        RoiMetrics roi = computeRoi(impact, kpis.totalSpend());

        Assumptions assumptions = new Assumptions(copilotMonthlySeatCostUsd, licensedSeatsAssumed,
                blendedHourlyRateUsd,
                "Claude Code cost is real per-day usage-based billing from the connector's usage report. "
                        + "GitHub Copilot has no per-request cost (flat-fee seat product) — its cost here is "
                        + "the configured monthly seat price allocated across active days only. Adoption rate "
                        + "is distinct active AI-tool users in the window divided by the assumed licensed seat "
                        + "count below (capped at 100% — Claude Code and Copilot identities aren't cross-tool "
                        + "resolved yet, so the same person using both tools can count twice). AI-04's cycle-time "
                        + "delta is computed from staging.pull_request_state.ai_assisted, detected from each PR's "
                        + "title/body/labels against known AI co-author trailer conventions — PRs with no such "
                        + "trailer are counted as non-AI-assisted, which undercounts AI involvement that leaves no "
                        + "trailer. AI-05's hours-saved and dollar-value-recovered figures multiply that delta by "
                        + "the blended hourly rate below — all four assumptions here are org-configurable, none "
                        + "are hidden constants.");

        return new AiCostTrackResponse(
                "Last " + windowDays + " days · Org-wide",
                windowDays, kpis, spendByTool, spendTrend, dailySpend, developerAllocation,
                false, impact, roi, assumptions);
    }

    private ImpactMetrics queryImpact(Date since) {
        Map<Boolean, BigDecimal> medianHours = new LinkedHashMap<>();
        Map<Boolean, Integer> counts = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT ai_assisted,
                       PERCENTILE_CONT(0.5) WITHIN GROUP (
                           ORDER BY EXTRACT(EPOCH FROM (merged_at - created_at)) / 3600.0
                       ) AS median_hours,
                       COUNT(*) AS pr_count
                FROM staging.pull_request_state
                WHERE merged_at >= ? AND merged_at IS NOT NULL AND created_at IS NOT NULL
                GROUP BY ai_assisted
                """, rs -> {
            while (rs.next()) {
                boolean aiAssisted = rs.getBoolean("ai_assisted");
                BigDecimal median = rs.getBigDecimal("median_hours");
                medianHours.put(aiAssisted, median == null ? BigDecimal.ZERO : median.setScale(1, RoundingMode.HALF_UP));
                counts.put(aiAssisted, rs.getInt("pr_count"));
            }
            return null;
        }, since);

        int aiCount = counts.getOrDefault(true, 0);
        int nonAiCount = counts.getOrDefault(false, 0);
        if (aiCount < MIN_PRS_PER_BUCKET || nonAiCount < MIN_PRS_PER_BUCKET) {
            return null;
        }
        int totalCount = aiCount + nonAiCount;
        BigDecimal aiShare = BigDecimal.valueOf(aiCount)
                .divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP);
        return new ImpactMetrics(medianHours.get(true), medianHours.get(false), aiCount, nonAiCount, aiShare);
    }

    private RoiMetrics computeRoi(ImpactMetrics impact, BigDecimal totalSpend) {
        if (impact == null || totalSpend.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal cycleDeltaHours = impact.nonAiMedianCycleHours().subtract(impact.aiAssistedMedianCycleHours());
        BigDecimal hoursSaved = cycleDeltaHours.multiply(BigDecimal.valueOf(impact.aiAssistedPrCount()))
                .setScale(1, RoundingMode.HALF_UP);
        BigDecimal dollarValueRecovered = hoursSaved.multiply(blendedHourlyRateUsd)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal roiMultiple = dollarValueRecovered.divide(totalSpend, 2, RoundingMode.HALF_UP);
        return new RoiMetrics(hoursSaved, dollarValueRecovered, roiMultiple, blendedHourlyRateUsd);
    }

    private Kpis queryKpis(Date since) {
        return jdbcTemplate.query("""
                SELECT COALESCE(SUM(cost_usd), 0) AS total_spend,
                       COALESCE(SUM(prs), 0) AS total_prs,
                       COUNT(*) AS active_user_days,
                       COUNT(DISTINCT actor_key) AS active_users
                FROM staging.ai_usage_state
                WHERE day >= ?
                """, rs -> {
            if (!rs.next()) {
                return new Kpis(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            }
            BigDecimal totalSpend = rs.getBigDecimal("total_spend");
            long totalPrs = rs.getLong("total_prs");
            long activeUserDays = rs.getLong("active_user_days");
            long activeUsers = rs.getLong("active_users");

            BigDecimal costPerPr = totalPrs == 0 ? BigDecimal.ZERO
                    : totalSpend.divide(BigDecimal.valueOf(totalPrs), 2, RoundingMode.HALF_UP);
            BigDecimal costPerDevDay = activeUserDays == 0 ? BigDecimal.ZERO
                    : totalSpend.divide(BigDecimal.valueOf(activeUserDays), 2, RoundingMode.HALF_UP);
            // Capped at 100%: activeUsers counts DISTINCT actor_key across tools, and Claude
            // Code/Copilot actor keys aren't cross-tool identity-resolved (an email and a GitHub
            // login for the same real person count as two "active users") — so this can
            // genuinely exceed licensedSeatsAssumed without being wrong, just double-counted.
            // Capping avoids a nonsensical >100% display; developerAllocation carries the
            // unclamped per-identity breakdown for anyone who wants the raw signal.
            BigDecimal adoptionRate = licensedSeatsAssumed == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(activeUsers)
                        .divide(BigDecimal.valueOf(licensedSeatsAssumed), 4, RoundingMode.HALF_UP)
                        .min(BigDecimal.ONE);

            return new Kpis(totalSpend, costPerPr, costPerDevDay, adoptionRate);
        }, since);
    }

    private List<SpendByTool> querySpendByTool(Date since) {
        return jdbcTemplate.query("""
                SELECT source, COALESCE(SUM(cost_usd), 0) AS spend
                FROM staging.ai_usage_state
                WHERE day >= ?
                GROUP BY source
                ORDER BY spend DESC
                """, (rs, rowNum) -> new SpendByTool(toolLabel(rs.getString("source")), rs.getBigDecimal("spend")), since);
    }

    private List<SpendTrendPoint> querySpendTrend(Date since) {
        return jdbcTemplate.query("""
                SELECT date_trunc('month', day)::date AS month,
                       COALESCE(SUM(cost_usd), 0) AS spend, COALESCE(SUM(prs), 0) AS prs
                FROM staging.ai_usage_state
                WHERE day >= ?
                GROUP BY 1 ORDER BY 1
                """, (rs, rowNum) -> {
            LocalDate month = rs.getDate("month").toLocalDate();
            String label = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.US);
            return new SpendTrendPoint(label, rs.getBigDecimal("spend"), rs.getInt("prs"));
        }, since);
    }

    private List<DailySpendPoint> queryDailySpend(Date since) {
        Map<LocalDate, Map<String, BigDecimal>> byDay = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT day, source, COALESCE(SUM(cost_usd), 0) AS spend
                FROM staging.ai_usage_state
                WHERE day >= ?
                GROUP BY day, source
                ORDER BY day
                """, rs -> {
            while (rs.next()) {
                LocalDate day = rs.getDate("day").toLocalDate();
                byDay.computeIfAbsent(day, d -> new LinkedHashMap<>())
                        .put(rs.getString("source"), rs.getBigDecimal("spend"));
            }
            return null;
        }, since);

        List<DailySpendPoint> points = new ArrayList<>();
        for (Map.Entry<LocalDate, Map<String, BigDecimal>> entry : byDay.entrySet()) {
            Map<String, BigDecimal> bySource = entry.getValue();
            points.add(new DailySpendPoint(
                    entry.getKey().toString(),
                    bySource.getOrDefault("claude_code", BigDecimal.ZERO),
                    bySource.getOrDefault("copilot", BigDecimal.ZERO),
                    BigDecimal.ZERO)); // Cursor: no connector yet
        }
        return points;
    }

    private List<DeveloperAllocation> queryDeveloperAllocation(Date since) {
        return jdbcTemplate.query("""
                SELECT source, actor_key, COALESCE(SUM(cost_usd), 0) AS spend, COALESCE(SUM(prs), 0) AS prs
                FROM staging.ai_usage_state
                WHERE day >= ?
                GROUP BY source, actor_key
                ORDER BY spend DESC
                """, (rs, rowNum) -> new DeveloperAllocation(
                        rs.getString("actor_key"), toolLabel(rs.getString("source")),
                        rs.getBigDecimal("spend"), rs.getInt("prs")),
                since);
    }

    private static String toolLabel(String source) {
        return "claude_code".equals(source) ? TOOL_LABEL_CLAUDE : TOOL_LABEL_COPILOT;
    }
}
