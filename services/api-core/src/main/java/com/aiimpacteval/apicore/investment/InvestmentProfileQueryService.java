package com.aiimpacteval.apicore.investment;

import com.aiimpacteval.apicore.investment.InvestmentProfileDtos.CategoryCount;
import com.aiimpacteval.apicore.investment.InvestmentProfileDtos.InvestmentProfileResponse;
import com.aiimpacteval.apicore.investment.InvestmentProfileDtos.MonthlyBreakdown;
import com.aiimpacteval.apicore.investment.InvestmentProfileDtos.TeamBreakdown;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Backs the Investment Profile tab (PRD E5-S1). Classifies each pull request as Planned /
 * Unplanned / Rework / Unclassifiable by extracting a Jira issue key from the PR title
 * (pattern: {@code PROJ-123}) and joining to {@code staging.jira_issue_state} (V10):
 * <ul>
 * <li>no issue key found in the title, or the key doesn't match a known issue → Unclassifiable
 * — surfaced as its own category rather than silently dropped or folded into Planned, per the
 * PRD's explicit "unclassifiable work bucketed transparently" requirement;</li>
 * <li>the matched issue was reopened after being marked done → Rework (best-effort signal, see
 * {@code staging.jira_issue_state.reopened});</li>
 * <li>issue type is Bug → Unplanned;</li>
 * <li>anything else (Story/Task/Epic/etc.) → Planned.</li>
 * </ul>
 *
 * <p>This is necessarily a heuristic — teams that don't reference Jira keys in PR titles/branch
 * names will show up as mostly Unclassifiable, which is the honest result, not a bug. Scope
 * resolution mirrors {@link com.aiimpacteval.apicore.codereview.CodeReviewQueryService}: these
 * tables aren't behind a pre-aggregated mart rollup yet.
 */
@Service
public class InvestmentProfileQueryService {

    private static final List<String> CATEGORIES = List.of("Planned", "Unplanned", "Rework", "Unclassifiable");

    private final JdbcTemplate jdbcTemplate;

    public InvestmentProfileQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public InvestmentProfileResponse investmentProfile(int windowDays, String scope) {
        List<String> repos = resolveRepos(scope);
        String reposCsv = repos == null ? null : String.join(",", repos);

        List<CategoryCount> breakdown = breakdown(windowDays, reposCsv);
        List<MonthlyBreakdown> trend = trend(windowDays, reposCsv);
        List<TeamBreakdown> byTeam = byTeam(windowDays, reposCsv);

        String scopeLabel = "*".equals(scope) ? "Org-wide"
                : (repos != null && repos.size() == 1) ? repos.get(0) : "Team";
        String windowLabel = "Last " + windowDays + " days · " + scopeLabel;

        return new InvestmentProfileResponse(windowLabel, breakdown, trend, byTeam);
    }

    /** @return repo full names to filter by, or {@code null} for no filter (org-wide). */
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

    // Shared by all three queries below: extracts a Jira key from the PR title and classifies it.
    private static final String CLASSIFIED_CTE = """
            WITH pr AS (
                SELECT p.repo, p.created_at,
                       substring(p.title from '([A-Z][A-Z0-9]{1,9}-[0-9]+)') AS issue_key
                FROM staging.pull_request_state p
                WHERE p.created_at >= now() - (? || ' days')::interval
                      AND (?::text IS NULL OR p.repo = ANY(string_to_array(?, ',')))
            ),
            classified AS (
                SELECT
                    pr.repo,
                    pr.created_at,
                    CASE
                        WHEN pr.issue_key IS NULL THEN 'Unclassifiable'
                        WHEN j.issue_key IS NULL THEN 'Unclassifiable'
                        WHEN j.reopened THEN 'Rework'
                        WHEN lower(j.issue_type) = 'bug' THEN 'Unplanned'
                        ELSE 'Planned'
                    END AS category
                FROM pr
                LEFT JOIN staging.jira_issue_state j ON j.issue_key = pr.issue_key
            )
            """;

    private List<CategoryCount> breakdown(int windowDays, String reposCsv) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        CATEGORIES.forEach(c -> counts.put(c, 0));

        jdbcTemplate.query(CLASSIFIED_CTE + "SELECT category, count(*) AS n FROM classified GROUP BY category",
                rs -> {
                    counts.put(rs.getString("category"), rs.getInt("n"));
                },
                windowDays, reposCsv, reposCsv);

        List<CategoryCount> result = new ArrayList<>();
        counts.forEach((category, count) -> result.add(new CategoryCount(category, count)));
        return result;
    }

    private List<MonthlyBreakdown> trend(int windowDays, String reposCsv) {
        // month_key sorts correctly (YYYY-MM); month_label is what the frontend actually shows.
        Map<String, int[]> byMonth = new LinkedHashMap<>();
        jdbcTemplate.query(CLASSIFIED_CTE + """
                SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS month_key,
                       to_char(date_trunc('month', created_at), 'Mon') AS month_label,
                       category, count(*) AS n
                FROM classified
                GROUP BY month_key, month_label, category
                ORDER BY month_key
                """,
                rs -> {
                    String key = rs.getString("month_label");
                    int[] counts = byMonth.computeIfAbsent(key, k -> new int[4]);
                    addToCategory(counts, rs.getString("category"), rs.getInt("n"));
                },
                windowDays, reposCsv, reposCsv);

        List<MonthlyBreakdown> result = new ArrayList<>();
        byMonth.forEach((month, c) -> result.add(new MonthlyBreakdown(month, c[0], c[1], c[2], c[3])));
        return result;
    }

    private List<TeamBreakdown> byTeam(int windowDays, String reposCsv) {
        Map<String, int[]> byTeamName = new LinkedHashMap<>();
        jdbcTemplate.query(CLASSIFIED_CTE + """
                SELECT t.name AS team, c.category, count(*) AS n
                FROM classified c
                JOIN core.team_repo tr ON tr.repo = c.repo
                JOIN core.team t ON t.id = tr.team_id
                GROUP BY t.name, c.category
                ORDER BY t.name
                """,
                rs -> {
                    String team = rs.getString("team");
                    int[] counts = byTeamName.computeIfAbsent(team, k -> new int[4]);
                    addToCategory(counts, rs.getString("category"), rs.getInt("n"));
                },
                windowDays, reposCsv, reposCsv);

        List<TeamBreakdown> result = new ArrayList<>();
        byTeamName.forEach((team, c) -> result.add(new TeamBreakdown(team, c[0], c[1], c[2], c[3])));
        return result;
    }

    // index order matches CATEGORIES: [planned, unplanned, rework, unclassifiable]
    private static void addToCategory(int[] counts, String category, int n) {
        int idx = CATEGORIES.indexOf(category);
        if (idx >= 0) {
            counts[idx] += n;
        }
    }
}