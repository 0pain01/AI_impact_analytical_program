package com.aiimpacteval.apicore.codereview;

import com.aiimpacteval.apicore.codereview.CodeReviewDtos.AgingPr;
import com.aiimpacteval.apicore.codereview.CodeReviewDtos.AgingPrsPage;
import com.aiimpacteval.apicore.codereview.CodeReviewDtos.CodeReviewResponse;
import com.aiimpacteval.apicore.codereview.CodeReviewDtos.PrCycleStage;
import com.aiimpacteval.apicore.codereview.CodeReviewDtos.ReviewerLoad;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Backs the Code Review tab (PRD PG-4): PR cycle-stage breakdown, review load per reviewer, and
 * a paged/sortable/filterable aging-PRs worklist. Reads {@code staging.pull_request_state}
 * (V5/V7) and {@code staging.pull_request_review_state} (V6) directly — unlike Cockpit's
 * metrics, these aren't backed by a nightly {@code mart.metric_daily} rollup yet, so scope
 * resolution (repo full name / team UUID / org "*") happens here in SQL rather than being a
 * literal {@code scope_id} match against a pre-scoped column (contrast with
 * {@link com.aiimpacteval.apicore.metrics.CockpitQueryService}).
 *
 * <p>cycleStages/reviewLoad are recomputed on every call regardless of the aging-PRs paging/sort/
 * filter params, since they're cheap aggregate queries and splitting this into three separate
 * endpoints wasn't worth the added round trips at current data volume.
 */
@Service
public class CodeReviewQueryService {

    private static final Set<String> VALID_SORT_BY = Set.of("age", "repo", "size");

    private final JdbcTemplate jdbcTemplate;

    public CodeReviewQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CodeReviewResponse codeReview(int windowDays, String scope, String repoFilter,
                                         String sortBy, String sortDir, int page, int pageSize) {
        List<String> repos = resolveRepos(scope);
        // repo full names ("owner/repo") never contain commas, so a CSV + string_to_array is a
        // simple stand-in for a real text[] bind parameter here.
        String reposCsv = repos == null ? null : String.join(",", repos);
        String trimmedRepoFilter = (repoFilter == null || repoFilter.isBlank()) ? null : repoFilter.trim();

        List<PrCycleStage> cycleStages = cycleStages(windowDays, reposCsv);
        List<ReviewerLoad> reviewLoad = reviewLoad(windowDays, reposCsv);
        AgingPrsPage agingPrs = agingPrs(windowDays, reposCsv, trimmedRepoFilter, sortBy, sortDir, page, pageSize);

        String scopeLabel = "*".equals(scope) ? "Org-wide"
                : (repos != null && repos.size() == 1) ? repos.get(0) : "Team";
        String windowLabel = "Last " + windowDays + " days · " + scopeLabel;

        return new CodeReviewResponse(windowLabel, cycleStages, reviewLoad, agingPrs);
    }

    /**
     * @param scope a repo full name, {@code "*"} for the org rollup, or a team UUID.
     * @return repo full names to filter by, or {@code null} for no filter (org-wide).
     */
    private List<String> resolveRepos(String scope) {
        if (scope == null || "*".equals(scope)) {
            return null;
        }
        try {
            UUID teamId = UUID.fromString(scope);
            return jdbcTemplate.queryForList(
                    "SELECT repo FROM core.team_repo WHERE team_id = ?", String.class, teamId);
        } catch (IllegalArgumentException notATeamUuid) {
            return List.of(scope);
        }
    }

    private List<PrCycleStage> cycleStages(int windowDays, String reposCsv) {
        // Three p50s in one pass: open->first review, first review->approval, approval->merge.
        // percentile_cont still returns a single row even with zero matching PRs, so no
        // GROUP BY / empty-result handling is needed here.
        Map<String, BigDecimal> byStage = jdbcTemplate.query("""
                WITH pr AS (
                    SELECT p.repo, p.number, p.created_at, p.merged_at
                    FROM staging.pull_request_state p
                    WHERE p.number IS NOT NULL
                          AND p.created_at >= now() - (? || ' days')::interval
                          AND (?::text IS NULL OR p.repo = ANY(string_to_array(?, ',')))
                ),
                first_review AS (
                    SELECT r.repo, r.pr_number, min(r.submitted_at) AS at
                    FROM staging.pull_request_review_state r
                    GROUP BY r.repo, r.pr_number
                ),
                first_approval AS (
                    SELECT r.repo, r.pr_number, min(r.submitted_at) AS at
                    FROM staging.pull_request_review_state r
                    WHERE r.state = 'APPROVED'
                    GROUP BY r.repo, r.pr_number
                )
                SELECT
                    percentile_cont(0.5) WITHIN GROUP (ORDER BY
                        EXTRACT(EPOCH FROM (fr.at - pr.created_at)) / 3600.0)
                        FILTER (WHERE fr.at IS NOT NULL) AS open_to_first_review,
                    percentile_cont(0.5) WITHIN GROUP (ORDER BY
                        EXTRACT(EPOCH FROM (fa.at - fr.at)) / 3600.0)
                        FILTER (WHERE fa.at IS NOT NULL AND fr.at IS NOT NULL) AS first_review_to_approval,
                    percentile_cont(0.5) WITHIN GROUP (ORDER BY
                        EXTRACT(EPOCH FROM (pr.merged_at - fa.at)) / 3600.0)
                        FILTER (WHERE pr.merged_at IS NOT NULL AND fa.at IS NOT NULL) AS approval_to_merge
                FROM pr
                LEFT JOIN first_review fr ON fr.repo = pr.repo AND fr.pr_number = pr.number
                LEFT JOIN first_approval fa ON fa.repo = pr.repo AND fa.pr_number = pr.number
                """,
                rs -> {
                    Map<String, BigDecimal> m = new LinkedHashMap<>();
                    if (rs.next()) {
                        m.put("Open → first review", round1(rs.getBigDecimal("open_to_first_review")));
                        m.put("First review → approval", round1(rs.getBigDecimal("first_review_to_approval")));
                        m.put("Approval → merge", round1(rs.getBigDecimal("approval_to_merge")));
                    }
                    return m;
                },
                windowDays, reposCsv, reposCsv);

        List<PrCycleStage> stages = new ArrayList<>();
        byStage.forEach((stage, hoursP50) -> stages.add(new PrCycleStage(stage, hoursP50)));
        return stages;
    }

    private List<ReviewerLoad> reviewLoad(int windowDays, String reposCsv) {
        return jdbcTemplate.query("""
                SELECT reviewer_login, count(*) AS reviews
                FROM staging.pull_request_review_state
                WHERE reviewer_login IS NOT NULL
                      AND submitted_at >= now() - (? || ' days')::interval
                      AND (?::text IS NULL OR repo = ANY(string_to_array(?, ',')))
                GROUP BY reviewer_login
                ORDER BY reviews DESC
                LIMIT 10
                """,
                (rs, rowNum) -> new ReviewerLoad(rs.getString("reviewer_login"), rs.getInt("reviews")),
                windowDays, reposCsv, reposCsv);
    }

    private AgingPrsPage agingPrs(int windowDays, String reposCsv, String repoFilter,
                                  String sortBy, String sortDir, int page, int pageSize) {
        // Scoped to the same window as cycleStages/reviewLoad — without this, repos with years
        // of history (e.g. large public OSS repos used as demo seed data) surface PRs that are
        // literally years old and were simply never closed, which drowns out anything actually
        // actionable. "Aging" means aging within the window under review, not "oldest ever."
        String whereSql = """
                WHERE state = 'open' AND number IS NOT NULL
                      AND created_at >= now() - (? || ' days')::interval
                      AND (?::text IS NULL OR repo = ANY(string_to_array(?, ',')))
                      AND (?::text IS NULL OR repo ILIKE '%' || ? || '%')
                """;
        Object[] whereArgs = {windowDays, reposCsv, reposCsv, repoFilter, repoFilter};

        Long totalCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM staging.pull_request_state " + whereSql, Long.class, whereArgs);

        // orderByClause is built entirely from a fixed allow-list below, never from the raw
        // sortBy/sortDir request strings directly — safe to concatenate into the SQL.
        String orderByClause = buildOrderByClause(sortBy, sortDir);
        int safeOffset = page * pageSize;

        List<AgingPr> items = jdbcTemplate.query(
                "SELECT repo, number, title, author, created_at, requested_reviewers "
                        + "FROM staging.pull_request_state " + whereSql
                        + "ORDER BY " + orderByClause + " LIMIT ? OFFSET ?",
                (rs, rowNum) -> {
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    long ageHours = createdAt == null ? 0
                            : Duration.between(createdAt.toInstant(), Instant.now()).toHours();
                    List<String> reviewers = toStringList(rs.getArray("requested_reviewers"));
                    long number = rs.getLong("number");
                    return new AgingPr("#" + number, rs.getString("title"), rs.getString("repo"),
                            rs.getString("author"), ageHours, reviewers, null);
                },
                windowDays, reposCsv, reposCsv, repoFilter, repoFilter, pageSize, safeOffset);

        return new AgingPrsPage(items, page, pageSize, totalCount == null ? 0 : totalCount);
    }

    /**
     * "size" is accepted but not yet backed by real data (see {@link AgingPr#sizeLines}), so it
     * falls back to age ordering rather than a no-op / arbitrary order.
     */
    private static String buildOrderByClause(String sortBy, String sortDir) {
        String normalizedSortBy = VALID_SORT_BY.contains(sortBy) ? sortBy : "age";
        boolean desc = !"asc".equalsIgnoreCase(sortDir);

        if ("repo".equals(normalizedSortBy)) {
            return "repo " + (desc ? "DESC" : "ASC") + ", created_at ASC";
        }
        // age DESC (the default) means "biggest age / oldest PR first", which is created_at ASC.
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