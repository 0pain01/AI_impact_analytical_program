package com.aiimpacteval.apicore.jira;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response DTOs for GET /api/v1/metrics/jira-work-items. Field names are deliberately close to
 * {@link com.aiimpacteval.apicore.codereview.CodeReviewDtos}' shape (windowLabel + a paged
 * worklist + supporting breakdowns) so the two dashboards read the same way in the UI.
 */
public final class JiraDashboardDtos {

    public record JiraDashboardResponse(String windowLabel, JiraKpis kpis,
                                        List<StatusCount> statusBreakdown,
                                        List<TypeCount> typeBreakdown,
                                        List<PriorityCount> priorityBreakdown,
                                        List<AssigneeLoad> topAssignees,
                                        List<LabelCount> topLabels,
                                        List<ResolutionTrendPoint> resolutionTrend,
                                        JiraIssuesPage issues) {
    }

    /**
     * openIssues/overdueCount reflect current backlog state (not window-scoped — an open issue
     * from 6 months ago is still open today). resolvedInWindow/medianResolutionHoursP50/
     * reopenRate are scoped to the window, like Code Review's cycle-stage figures.
     */
    public record JiraKpis(long openIssues, long resolvedInWindow, BigDecimal medianResolutionHoursP50,
                           BigDecimal reopenRate, long overdueCount) {
    }

    public record StatusCount(String statusCategory, String label, long count) {
    }

    public record TypeCount(String issueType, long count) {
    }

    public record PriorityCount(String priority, long count) {
    }

    public record AssigneeLoad(String assignee, long openCount) {
    }

    /** "Topics" — Jira label frequency across open issues in scope. */
    public record LabelCount(String label, long count) {
    }

    public record ResolutionTrendPoint(String weekStart, BigDecimal medianResolutionHours) {
    }

    public record JiraIssuesPage(List<JiraIssue> items, int page, int pageSize, long totalCount) {
    }

    public record JiraIssue(String issueKey, String summary, String projectKey, String issueType,
                            String status, String statusCategory, String priority, String assignee,
                            String reporter, long ageDays, LocalDate dueDate, boolean overdue,
                            List<String> labels) {
    }

    private JiraDashboardDtos() {
    }
}
