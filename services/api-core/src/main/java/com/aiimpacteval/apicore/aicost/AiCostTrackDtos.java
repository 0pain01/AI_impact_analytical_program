package com.aiimpacteval.apicore.aicost;

import java.math.BigDecimal;
import java.util.List;

/** Response contract for GET /api/v1/metrics/ai-cost-track. Contract: openapi/api-core.yml. */
public final class AiCostTrackDtos {

    private AiCostTrackDtos() {
    }

    public record Kpis(BigDecimal totalSpend, BigDecimal costPerPr, BigDecimal costPerDevDay, BigDecimal adoptionRate) {
    }

    public record SpendByTool(String tool, BigDecimal monthlySpend) {
    }

    public record SpendTrendPoint(String month, BigDecimal spend, int prsAssisted) {
    }

    public record DailySpendPoint(String date, BigDecimal claudeCode, BigDecimal githubCopilot, BigDecimal cursor) {
    }

    public record DeveloperAllocation(String developer, String tool, BigDecimal spend, int mergedPrs) {
    }

    /**
     * AI-04: median PR cycle time (created→merged) for AI-assisted vs. non-AI-assisted PRs,
     * segmented by {@code staging.pull_request_state.ai_assisted} (V13 migration). Null when
     * either bucket has too few merged PRs in the window for a median to be meaningful.
     */
    public record ImpactMetrics(
            BigDecimal aiAssistedMedianCycleHours,
            BigDecimal nonAiMedianCycleHours,
            int aiAssistedPrCount,
            int nonAiPrCount,
            BigDecimal aiAssistedShare) {
    }

    /**
     * AI-05: {@code estimatedHoursSaved = aiAssistedPrCount × (nonAiMedianCycleHours −
     * aiAssistedMedianCycleHours)}, {@code dollarValueRecovered = estimatedHoursSaved ×
     * blendedHourlyRateUsd}, {@code roiMultiple = dollarValueRecovered / totalSpend}. Can be
     * negative — a negative delta means AI-assisted PRs took longer in this window, and that's
     * reported as-is rather than floored at zero.
     */
    public record RoiMetrics(
            BigDecimal estimatedHoursSaved,
            BigDecimal dollarValueRecovered,
            BigDecimal roiMultiple,
            BigDecimal blendedHourlyRateUsd) {
    }

    /**
     * {@code teamAllocation}/{@code adoptionByTeam} are always empty (not absent) — no
     * contributor→team mapping exists for AI-telemetry actor keys yet (identity resolution
     * hasn't been extended to Claude Code emails / Copilot logins), so team-level breakdown is
     * genuinely unavailable rather than fabricated. {@code impact}/{@code roi} are null (not a
     * zeroed-out object) when the underlying PR sample in the window is too small to segment —
     * the frontend shows an honest "not available yet" state in that case rather than a noisy or
     * fabricated number.
     */
    public record AiCostTrackResponse(
            String windowLabel,
            int windowDays,
            Kpis kpis,
            List<SpendByTool> spendByTool,
            List<SpendTrendPoint> spendTrend,
            List<DailySpendPoint> dailySpend,
            List<DeveloperAllocation> developerAllocation,
            boolean teamAllocationAvailable,
            ImpactMetrics impact,
            RoiMetrics roi,
            Assumptions assumptions) {
    }

    /** Every non-observed input this computation relies on — never a hidden constant (AI-01/AI-05). */
    public record Assumptions(
            BigDecimal copilotMonthlySeatCostUsd,
            int licensedSeatsAssumed,
            BigDecimal blendedHourlyRateUsd,
            String methodologyNote) {
    }
}
