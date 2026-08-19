package com.aiimpacteval.apicore.investment;

import java.util.List;

/**
 * Response DTOs for GET /api/v1/metrics/investment-profile (PRD E5-S1: "Git activity correlated
 * to Jira epics classifies each unit of work; split shown per team over time with category
 * definitions; unclassifiable work bucketed transparently").
 *
 * <p>Counts of pull requests, not hours. Jira worklogs (time actually logged against an issue)
 * are sparse-to-nonexistent on most teams, so fabricating an "hours" figure from them would be
 * precision theater — a PR count per category is honest about what the data actually supports.
 */
public final class InvestmentProfileDtos {

    public record InvestmentProfileResponse(String windowLabel, List<CategoryCount> breakdown,
                                            List<MonthlyBreakdown> trend, List<TeamBreakdown> byTeam) {
    }

    public record CategoryCount(String category, int count) {
    }

    public record MonthlyBreakdown(String month, int planned, int unplanned, int rework, int unclassifiable) {
    }

    public record TeamBreakdown(String team, int planned, int unplanned, int rework, int unclassifiable) {
    }

    private InvestmentProfileDtos() {
    }
}