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

    /**
     * One PR/MR with exactly what it was classified from and what it matched — the "can I verify
     * this is actually the right ticket" drill-down. {@code extractedIssueKey} is whatever the
     * title-regex found (or null if nothing matched the pattern at all) — shown even when it
     * didn't resolve to a real issue, so a typo is visible as a typo rather than just vanishing
     * into "Unclassifiable" with no explanation. The {@code jira*} fields are null together
     * whenever there was no match; {@code jiraUrl} is null even on a real match if
     * {@code jira.site-base-url} isn't configured (no fabricated link pattern).
     */
    public record LinkedPr(String repo, String prId, Long number, String title, String author,
                           String htmlUrl, String createdAt, String category, String extractedIssueKey,
                           String jiraIssueKey, String jiraSummary, String jiraProjectKey, String jiraStatus,
                           String jiraUrl) {
    }

    public record LinkedPrsPage(List<LinkedPr> items, int page, int pageSize, long totalCount) {
    }

    private InvestmentProfileDtos() {
    }
}