package com.aiimpacteval.apicore.jira;

import com.aiimpacteval.apicore.jira.JiraDashboardDtos.AssigneeLoad;
import com.aiimpacteval.apicore.jira.JiraDashboardDtos.JiraDashboardResponse;
import com.aiimpacteval.apicore.jira.JiraDashboardDtos.JiraIssue;
import com.aiimpacteval.apicore.jira.JiraDashboardDtos.JiraIssuesPage;
import com.aiimpacteval.apicore.jira.JiraDashboardDtos.JiraKpis;
import com.aiimpacteval.apicore.jira.JiraDashboardDtos.LabelCount;
import com.aiimpacteval.apicore.jira.JiraDashboardDtos.PriorityCount;
import com.aiimpacteval.apicore.jira.JiraDashboardDtos.ResolutionTrendPoint;
import com.aiimpacteval.apicore.jira.JiraDashboardDtos.StatusCount;
import com.aiimpacteval.apicore.jira.JiraDashboardDtos.TypeCount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Array;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Backs the Jira Work Items dashboard: current backlog composition (status/type/priority/
 * assignee/label breakdowns, overdue count), window-scoped resolution metrics (median resolution
 * time, reopen rate, weekly resolution trend), and a paged/sortable/filterable open-issue
 * worklist. Reads {@code staging.jira_issue_state} (V10/V14) directly — same "no mart rollup yet,
 * scope resolved in SQL" shape as {@link com.aiimpacteval.apicore.codereview.CodeReviewQueryService}.
 *
 * <p><b>Current-state vs. window-scoped, deliberately mixed on one response</b> (see each field's
 * javadoc on {@link JiraKpis}): an open Bug filed eight months ago is still real backlog and must
 * not vanish from "issues by type" just because it's older than the trailing window — so
 * backlog-composition breakdowns and the worklist read current state (open = {@code resolved_at
 * IS NULL}), unwindowed. {@code statusBreakdown} is the one exception: it includes resolved
 * issues too (windowed by {@code created_at}, not filtered to open), because otherwise "Done"
 * would never appear in a chart whose entire point is showing the To Do/In Progress/Done shape of
 * the pipeline.
 */
@Service
public class JiraDashboardQueryService {

    private static final Set<String> VALID_SORT_BY = Set.of("age", "priority", "project");
    private static final int TOP_N = 10;

    private final JdbcTemplate jdbcTemplate;

    public JiraDashboardQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public JiraDashboardResponse jiraWorkItems(int windowDays, String project, String search,
                                               String sortBy, String sortDir, int page, int pageSize) {
        String projectFilter = (project == null || project.isBlank() || "*".equals(project)) ? null : project.trim();
        String trimmedSearch = (search == null || search.isBlank()) ? null : search.trim();

        JiraKpis kpis = kpis(windowDays, projectFilter);
        List<StatusCount> statusBreakdown = statusBreakdown(windowDays, projectFilter);
        List<TypeCount> typeBreakdown = typeBreakdown(projectFilter);
        List<PriorityCount> priorityBreakdown = priorityBreakdown(projectFilter);
        List<AssigneeLoad> topAssignees = topAssignees(projectFilter);
        List<LabelCount> topLabels = topLabels(projectFilter);
        List<ResolutionTrendPoint> resolutionTrend = resolutionTrend(windowDays, projectFilter);
        JiraIssuesPage issues = issuesPage(projectFilter, trimmedSearch, sortBy, sortDir, page, pageSize);

        String scopeLabel = projectFilter == null ? "All projects" : projectFilter;
        String windowLabel = "Last " + windowDays + " days · " + scopeLabel;

        return new JiraDashboardResponse(windowLabel, kpis, statusBreakdown, typeBreakdown,
                priorityBreakdown, topAssignees, topLabels, resolutionTrend, issues);
    }

    private JiraKpis kpis(int windowDays, String projectFilter) {
        Long openIssues = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM staging.jira_issue_state
                WHERE resolved_at IS NULL AND (?::text IS NULL OR project_key = ?)
                """, Long.class, projectFilter, projectFilter);

        Long overdueCount = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM staging.jira_issue_state
                WHERE resolved_at IS NULL AND due_date IS NOT NULL AND due_date < current_date
                      AND (?::text IS NULL OR project_key = ?)
                """, Long.class, projectFilter, projectFilter);

        Long resolvedInWindow = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM staging.jira_issue_state
                WHERE resolved_at IS NOT NULL AND resolved_at >= now() - (? || ' days')::interval
                      AND (?::text IS NULL OR project_key = ?)
                """, Long.class, windowDays, projectFilter, projectFilter);

        BigDecimal medianResolutionHoursP50 = jdbcTemplate.queryForObject("""
                SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY
                    EXTRACT(EPOCH FROM (resolved_at - created_at)) / 3600.0)
                FROM staging.jira_issue_state
                WHERE resolved_at IS NOT NULL AND created_at IS NOT NULL
                      AND resolved_at >= now() - (? || ' days')::interval
                      AND (?::text IS NULL OR project_key = ?)
                """, BigDecimal.class, windowDays, projectFilter, projectFilter);

        Long reopenedInWindow = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM staging.jira_issue_state
                WHERE resolved_at IS NOT NULL AND reopened = TRUE
                      AND resolved_at >= now() - (? || ' days')::interval
                      AND (?::text IS NULL OR project_key = ?)
                """, Long.class, windowDays, projectFilter, projectFilter);

        long resolved = resolvedInWindow == null ? 0 : resolvedInWindow;
        BigDecimal reopenRate = resolved == 0 ? null
                : BigDecimal.valueOf(reopenedInWindow == null ? 0 : reopenedInWindow)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(resolved), 1, RoundingMode.HALF_UP);

        return new JiraKpis(openIssues == null ? 0 : openIssues, resolved, round1(medianResolutionHoursP50),
                reopenRate, overdueCount == null ? 0 : overdueCount);
    }

    private static final List<String> STATUS_CATEGORY_ORDER = List.of("new", "indeterminate", "done");
    private static final java.util.Map<String, String> STATUS_CATEGORY_LABELS = java.util.Map.of(
            "new", "To Do", "indeterminate", "In Progress", "done", "Done");

    private List<StatusCount> statusBreakdown(int windowDays, String projectFilter) {
        // Deliberately not filtered to open issues — see class javadoc.
        var byCategory = jdbcTemplate.query("""
                SELECT coalesce(status_category, 'unknown') AS category, count(*) AS n
                FROM staging.jira_issue_state
                WHERE created_at >= now() - (? || ' days')::interval
                      AND (?::text IS NULL OR project_key = ?)
                GROUP BY category
                """,
                rs -> {
                    var m = new java.util.LinkedHashMap<String, Long>();
                    while (rs.next()) {
                        m.put(rs.getString("category"), rs.getLong("n"));
                    }
                    return m;
                },
                windowDays, projectFilter, projectFilter);

        List<StatusCount> result = new ArrayList<>();
        for (String key : STATUS_CATEGORY_ORDER) {
            Long count = byCategory == null ? null : byCategory.remove(key);
            if (count != null) {
                result.add(new StatusCount(key, STATUS_CATEGORY_LABELS.get(key), count));
            }
        }
        if (byCategory != null) {
            byCategory.forEach((key, count) -> result.add(new StatusCount(key, "Unknown", count)));
        }
        return result;
    }

    private List<TypeCount> typeBreakdown(String projectFilter) {
        return jdbcTemplate.query("""
                SELECT coalesce(issue_type, 'Unspecified') AS issue_type, count(*) AS n
                FROM staging.jira_issue_state
                WHERE resolved_at IS NULL AND (?::text IS NULL OR project_key = ?)
                GROUP BY issue_type ORDER BY n DESC
                """,
                (rs, rowNum) -> new TypeCount(rs.getString("issue_type"), rs.getLong("n")),
                projectFilter, projectFilter);
    }

    private List<PriorityCount> priorityBreakdown(String projectFilter) {
        return jdbcTemplate.query("""
                SELECT coalesce(priority, 'No priority') AS priority, count(*) AS n
                FROM staging.jira_issue_state
                WHERE resolved_at IS NULL AND (?::text IS NULL OR project_key = ?)
                GROUP BY priority ORDER BY n DESC
                """,
                (rs, rowNum) -> new PriorityCount(rs.getString("priority"), rs.getLong("n")),
                projectFilter, projectFilter);
    }

    private List<AssigneeLoad> topAssignees(String projectFilter) {
        return jdbcTemplate.query("""
                SELECT coalesce(assignee, 'Unassigned') AS assignee, count(*) AS n
                FROM staging.jira_issue_state
                WHERE resolved_at IS NULL AND (?::text IS NULL OR project_key = ?)
                GROUP BY assignee ORDER BY n DESC LIMIT ?
                """,
                (rs, rowNum) -> new AssigneeLoad(rs.getString("assignee"), rs.getLong("n")),
                projectFilter, projectFilter, TOP_N);
    }

    /** "Topics" — most common labels across the open backlog in scope. */
    private List<LabelCount> topLabels(String projectFilter) {
        return jdbcTemplate.query("""
                SELECT label, count(*) AS n
                FROM staging.jira_issue_state, unnest(labels) AS label
                WHERE resolved_at IS NULL AND (?::text IS NULL OR project_key = ?)
                GROUP BY label ORDER BY n DESC LIMIT ?
                """,
                (rs, rowNum) -> new LabelCount(rs.getString("label"), rs.getLong("n")),
                projectFilter, projectFilter, TOP_N);
    }

    private List<ResolutionTrendPoint> resolutionTrend(int windowDays, String projectFilter) {
        return jdbcTemplate.query("""
                SELECT date_trunc('week', resolved_at)::date AS week_start,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY
                           EXTRACT(EPOCH FROM (resolved_at - created_at)) / 3600.0) AS median_hours
                FROM staging.jira_issue_state
                WHERE resolved_at IS NOT NULL AND created_at IS NOT NULL
                      AND resolved_at >= now() - (? || ' days')::interval
                      AND (?::text IS NULL OR project_key = ?)
                GROUP BY week_start ORDER BY week_start
                """,
                (rs, rowNum) -> new ResolutionTrendPoint(rs.getDate("week_start").toString(),
                        round1(rs.getBigDecimal("median_hours"))),
                windowDays, projectFilter, projectFilter);
    }

    private JiraIssuesPage issuesPage(String projectFilter, String search, String sortBy, String sortDir,
                                      int page, int pageSize) {
        String whereSql = """
                WHERE resolved_at IS NULL
                      AND (?::text IS NULL OR project_key = ?)
                      AND (?::text IS NULL OR issue_key ILIKE '%' || ? || '%' OR summary ILIKE '%' || ? || '%')
                """;
        Object[] whereArgs = {projectFilter, projectFilter, search, search, search};

        Long totalCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM staging.jira_issue_state " + whereSql, Long.class, whereArgs);

        // orderByClause is built entirely from a fixed allow-list below, never the raw request
        // strings — safe to concatenate into the SQL (same pattern as CodeReviewQueryService).
        String orderByClause = buildOrderByClause(sortBy, sortDir);
        int safeOffset = page * pageSize;

        List<JiraIssue> items = jdbcTemplate.query(
                "SELECT issue_key, summary, project_key, issue_type, status, status_category, "
                        + "priority, assignee, reporter, created_at, due_date, labels "
                        + "FROM staging.jira_issue_state " + whereSql
                        + "ORDER BY " + orderByClause + " LIMIT ? OFFSET ?",
                (rs, rowNum) -> {
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    long ageDays = createdAt == null ? 0
                            : Duration.between(createdAt.toInstant(), Instant.now()).toDays();
                    Date dueDateSql = rs.getDate("due_date");
                    LocalDate dueDate = dueDateSql == null ? null : dueDateSql.toLocalDate();
                    boolean overdue = dueDate != null && dueDate.isBefore(LocalDate.now());
                    return new JiraIssue(rs.getString("issue_key"), rs.getString("summary"),
                            rs.getString("project_key"), rs.getString("issue_type"), rs.getString("status"),
                            rs.getString("status_category"), rs.getString("priority"), rs.getString("assignee"),
                            rs.getString("reporter"), ageDays, dueDate, overdue, toStringList(rs.getArray("labels")));
                },
                projectFilter, projectFilter, search, search, search, pageSize, safeOffset);

        return new JiraIssuesPage(items, page, pageSize, totalCount == null ? 0 : totalCount);
    }

    private static String buildOrderByClause(String sortBy, String sortDir) {
        String normalizedSortBy = VALID_SORT_BY.contains(sortBy) ? sortBy : "age";
        boolean desc = !"asc".equalsIgnoreCase(sortDir);

        if ("project".equals(normalizedSortBy)) {
            return "project_key " + (desc ? "DESC" : "ASC") + ", created_at ASC";
        }
        if ("priority".equals(normalizedSortBy)) {
            // No inherent ordinal on Jira priority names across instances — alphabetical is the
            // only sort that doesn't guess at a scheme (Highest > High > Medium > ... isn't
            // universal across custom priority schemes).
            return "priority " + (desc ? "DESC NULLS LAST" : "ASC NULLS LAST") + ", created_at ASC";
        }
        // age DESC (the default) means "oldest issue first", which is created_at ASC.
        return "created_at " + (desc ? "ASC" : "DESC");
    }

    private static List<String> toStringList(Array sqlArray) {
        if (sqlArray == null) {
            return List.of();
        }
        try {
            Object[] raw = (Object[]) sqlArray.getArray();
            List<String> out = new ArrayList<>(raw.length);
            for (Object o : raw) {
                if (o != null) {
                    out.add(o.toString());
                }
            }
            return out;
        } catch (SQLException e) {
            return List.of();
        }
    }

    private static BigDecimal round1(BigDecimal v) {
        return v == null ? null : v.setScale(1, RoundingMode.HALF_UP);
    }
}
