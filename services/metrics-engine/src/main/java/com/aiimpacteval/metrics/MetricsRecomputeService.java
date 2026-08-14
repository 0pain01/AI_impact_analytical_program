package com.aiimpacteval.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Recomputes the daily metric mart from staged events (metric-definitions.md is the contract;
 * bump METRIC_LOGIC_VERSION whenever a formula changes).
 *
 * <p>Design constraints honored here:
 * <ul>
 *   <li>Full-window recompute from immutable staging — results are reproducible and
 *       self-healing after replays (architecture §6).</li>
 *   <li>Latest-state dedup: staging holds every version of an entity (webhook + snapshots);
 *       each PR/run contributes once, from its most recent payload.</li>
 *   <li>Payload normalization: webhook payloads nest the entity ({@code payload.pull_request},
 *       {@code payload.workflow_run}); backfill snapshots ARE the entity. COALESCE handles both.</li>
 * </ul>
 *
 * <p>Metrics (all four DORA + PR analytics):
 * {@code deployment_frequency} (DORA-1), {@code lead_time_p50_hours} (DORA-2, heuristic
 * PR-open→first prod deploy after merge), {@code change_failure_rate} (DORA-3, deploy followed
 * within 48h by a hotfix/rollback), {@code mttr_p50_hours} (DORA-4), {@code pr_velocity},
 * {@code pr_cycle_time_p50_hours}. The full lead-time stage breakdown (review stages) awaits
 * PR-review ingestion.
 *
 * <p>Each metric materializes at three scopes (E4-S2 org → team drill-down): {@code repo}
 * (per repository), {@code org} ({@code scope_id = '*'}, all repos), and {@code team} (joined
 * via {@code core.team_repo}, populated by the identity service's team import — E2-S2). Team
 * rollups are computed from the same per-event rows as the repo/org branches, never by
 * averaging repo-day medians — percentiles don't compose that way.
 */
@Service
public class MetricsRecomputeService {

    static final String METRIC_LOGIC_VERSION = "v1";
    private static final Logger log = LoggerFactory.getLogger(MetricsRecomputeService.class);

    private static final String UPSERT = """
            INSERT INTO mart.metric_daily (metric_key, scope_id, scope_type, day, value, sample_size, metric_logic_version)
            %s
            ON CONFLICT ON CONSTRAINT uq_metric_daily
            DO UPDATE SET value = EXCLUDED.value, sample_size = EXCLUDED.sample_size,
                          metric_logic_version = EXCLUDED.metric_logic_version, computed_at = now()
            """;

    /** Latest state per workflow run — now a plain typed read, not a JSONB dedup (see V5 migration). */
    private static final String RUNS_CTE = """
            runs AS (
                SELECT repo, conclusion, name, ts FROM staging.workflow_run_state
            )
            """;

    /** Latest state per merged PR — now a plain typed read, not a JSONB dedup (see V5 migration). */
    private static final String MERGED_PRS_CTE = """
            merged AS (
                SELECT repo, created_at, merged_at
                FROM staging.pull_request_state
                WHERE merged_at IS NOT NULL
            )
            """;

    // ?1 deployPattern, ?2 windowDays
    private static final String DEPLOYMENT_FREQUENCY = "WITH " + RUNS_CTE + """
            , deploys AS (
                SELECT repo, (ts AT TIME ZONE 'UTC')::date AS day FROM runs
                WHERE conclusion = 'success' AND name ~* ? AND ts >= now() - make_interval(days => ?)
            )
            SELECT 'deployment_frequency', repo, 'repo', day, count(*)::numeric, count(*)::int, 'v1'
            FROM deploys GROUP BY repo, day
            UNION ALL
            SELECT 'deployment_frequency', '*', 'org', day, count(*)::numeric, count(*)::int, 'v1'
            FROM deploys GROUP BY day
            UNION ALL
            SELECT 'deployment_frequency', tr.team_id::text, 'team', d.day, count(*)::numeric, count(*)::int, 'v1'
            FROM deploys d JOIN core.team_repo tr ON tr.repo = d.repo GROUP BY tr.team_id, d.day
            """;

    // ?1 windowDays
    private static final String PR_VELOCITY = "WITH " + MERGED_PRS_CTE + """
            , w AS (SELECT repo, (merged_at AT TIME ZONE 'UTC')::date AS day FROM merged
                    WHERE merged_at >= now() - make_interval(days => ?))
            SELECT 'pr_velocity', repo, 'repo', day, count(*)::numeric, count(*)::int, 'v1'
            FROM w GROUP BY repo, day
            UNION ALL
            SELECT 'pr_velocity', '*', 'org', day, count(*)::numeric, count(*)::int, 'v1'
            FROM w GROUP BY day
            UNION ALL
            SELECT 'pr_velocity', tr.team_id::text, 'team', w.day, count(*)::numeric, count(*)::int, 'v1'
            FROM w JOIN core.team_repo tr ON tr.repo = w.repo GROUP BY tr.team_id, w.day
            """;

    // ?1 windowDays
    private static final String PR_CYCLE_TIME = "WITH " + MERGED_PRS_CTE + """
            , w AS (SELECT repo, (merged_at AT TIME ZONE 'UTC')::date AS day,
                           EXTRACT(EPOCH FROM merged_at - created_at) / 3600.0 AS hours
                    FROM merged WHERE merged_at >= now() - make_interval(days => ?))
            SELECT 'pr_cycle_time_p50_hours', repo, 'repo', day,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY hours)::numeric, count(*)::int, 'v1'
            FROM w GROUP BY repo, day
            UNION ALL
            SELECT 'pr_cycle_time_p50_hours', '*', 'org', day,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY hours)::numeric, count(*)::int, 'v1'
            FROM w GROUP BY day
            UNION ALL
            SELECT 'pr_cycle_time_p50_hours', tr.team_id::text, 'team', w.day,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY w.hours)::numeric, count(*)::int, 'v1'
            FROM w JOIN core.team_repo tr ON tr.repo = w.repo GROUP BY tr.team_id, w.day
            """;

    // ?1 deployPattern, ?2 windowDays (deploys), ?3 windowDays (merged window)
    private static final String LEAD_TIME = "WITH " + RUNS_CTE + ", " + MERGED_PRS_CTE + """
            , deploys AS (SELECT repo, ts FROM runs
                          WHERE conclusion = 'success' AND name ~* ? AND ts >= now() - make_interval(days => ?)),
            mw AS (SELECT repo, created_at, merged_at FROM merged WHERE merged_at >= now() - make_interval(days => ?)),
            linked AS (
                SELECT mw.repo, mw.created_at,
                       (SELECT min(d.ts) FROM deploys d WHERE d.repo = mw.repo AND d.ts >= mw.merged_at) AS deploy_ts
                FROM mw
            ),
            lt AS (SELECT repo, (deploy_ts AT TIME ZONE 'UTC')::date AS day,
                          EXTRACT(EPOCH FROM deploy_ts - created_at) / 3600.0 AS hours
                   FROM linked WHERE deploy_ts IS NOT NULL)
            SELECT 'lead_time_p50_hours', repo, 'repo', day,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY hours)::numeric, count(*)::int, 'v1'
            FROM lt GROUP BY repo, day
            UNION ALL
            SELECT 'lead_time_p50_hours', '*', 'org', day,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY hours)::numeric, count(*)::int, 'v1'
            FROM lt GROUP BY day
            UNION ALL
            SELECT 'lead_time_p50_hours', tr.team_id::text, 'team', lt.day,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY lt.hours)::numeric, count(*)::int, 'v1'
            FROM lt JOIN core.team_repo tr ON tr.repo = lt.repo GROUP BY tr.team_id, lt.day
            """;

    // ?1 deployPattern, ?2 windowDays, ?3 hotfixPattern
    private static final String CHANGE_FAILURE_RATE = "WITH " + RUNS_CTE + """
            , deploys AS (SELECT repo, ts FROM runs
                          WHERE conclusion = 'success' AND name ~* ? AND ts >= now() - make_interval(days => ?)),
            remed AS (SELECT repo, ts FROM runs WHERE conclusion = 'success' AND name ~* ?),
            marked AS (
                SELECT d.repo, (d.ts AT TIME ZONE 'UTC')::date AS day,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM remed r WHERE r.repo = d.repo
                             AND r.ts >= d.ts AND r.ts <= d.ts + interval '48 hours'
                       ) THEN 1 ELSE 0 END AS failed
                FROM deploys d
            )
            SELECT 'change_failure_rate', repo, 'repo', day, (sum(failed)::numeric / count(*)), count(*)::int, 'v1'
            FROM marked GROUP BY repo, day
            UNION ALL
            SELECT 'change_failure_rate', '*', 'org', day, (sum(failed)::numeric / count(*)), count(*)::int, 'v1'
            FROM marked GROUP BY day
            UNION ALL
            SELECT 'change_failure_rate', tr.team_id::text, 'team', marked.day,
                   (sum(marked.failed)::numeric / count(*)), count(*)::int, 'v1'
            FROM marked JOIN core.team_repo tr ON tr.repo = marked.repo GROUP BY tr.team_id, marked.day
            """;

    // ?1 deployPattern, ?2 windowDays, ?3 hotfixPattern
    private static final String MTTR = "WITH " + RUNS_CTE + """
            , deploys AS (SELECT repo, ts FROM runs
                          WHERE conclusion = 'success' AND name ~* ? AND ts >= now() - make_interval(days => ?)),
            remed AS (SELECT repo, ts FROM runs WHERE conclusion = 'success' AND name ~* ?),
            failed AS (
                SELECT d.repo, d.ts,
                       (SELECT min(r.ts) FROM remed r WHERE r.repo = d.repo
                          AND r.ts >= d.ts AND r.ts <= d.ts + interval '48 hours') AS restore_ts
                FROM deploys d
            ),
            mt AS (SELECT repo, (ts AT TIME ZONE 'UTC')::date AS day,
                          EXTRACT(EPOCH FROM restore_ts - ts) / 3600.0 AS hours
                   FROM failed WHERE restore_ts IS NOT NULL)
            SELECT 'mttr_p50_hours', repo, 'repo', day,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY hours)::numeric, count(*)::int, 'v1'
            FROM mt GROUP BY repo, day
            UNION ALL
            SELECT 'mttr_p50_hours', '*', 'org', day,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY hours)::numeric, count(*)::int, 'v1'
            FROM mt GROUP BY day
            UNION ALL
            SELECT 'mttr_p50_hours', tr.team_id::text, 'team', mt.day,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY mt.hours)::numeric, count(*)::int, 'v1'
            FROM mt JOIN core.team_repo tr ON tr.repo = mt.repo GROUP BY tr.team_id, mt.day
            """;

    private final JdbcTemplate jdbcTemplate;
    private final int windowDays;
    private final String deployWorkflowPattern;
    private final String hotfixWorkflowPattern;

    public MetricsRecomputeService(JdbcTemplate jdbcTemplate,
                                   @Value("${metrics.window-days}") int windowDays,
                                   @Value("${metrics.deploy-workflow-pattern}") String deployWorkflowPattern,
                                   @Value("${metrics.hotfix-workflow-pattern}") String hotfixWorkflowPattern) {
        this.jdbcTemplate = jdbcTemplate;
        this.windowDays = windowDays;
        this.deployWorkflowPattern = deployWorkflowPattern;
        this.hotfixWorkflowPattern = hotfixWorkflowPattern;
    }

    // Live progress tracker for recomputeAll(), polled via GET /internal/recompute/status.
    // Postgres has no generic per-row progress for an arbitrary INSERT...SELECT (unlike VACUUM
    // or CREATE INDEX, which do expose pg_stat_progress_* views), so true "N% of rows done"
    // isn't available from a single statement. What IS honest and useful: recomputeAll() already
    // runs as 6 sequential UPSERTs, so we report which stage is currently running (i/6) plus how
    // long that stage has been running. A stage number that keeps advancing means it's working;
    // a stage number frozen for many minutes means something is genuinely stuck — that
    // distinction is exactly what "is this stuck?" needs, without pretending to a precision we
    // can't actually measure mid-statement.
    private static final String[] STAGE_NAMES = {
            "deployment_frequency", "lead_time", "change_failure_rate", "mttr", "pr_velocity", "pr_cycle_time"
    };

    public record RecomputeStatus(boolean inProgress, int stageIndex, int totalStages, String stageName,
                                  long stageElapsedMs, Instant startedAt) {
    }

    private volatile boolean inProgress = false;
    private volatile int stageIndex = 0;
    private volatile long stageStartedAt = 0;
    private volatile Instant runStartedAt = null;

    public RecomputeStatus currentStatus() {
        if (!inProgress) {
            return new RecomputeStatus(false, 0, STAGE_NAMES.length, null, 0, null);
        }
        String name = stageIndex >= 1 && stageIndex <= STAGE_NAMES.length ? STAGE_NAMES[stageIndex - 1] : null;
        return new RecomputeStatus(true, stageIndex, STAGE_NAMES.length, name,
                System.currentTimeMillis() - stageStartedAt, runStartedAt);
    }

    private void enterStage(int index) {
        stageIndex = index;
        stageStartedAt = System.currentTimeMillis();
        log.info("Recompute stage {}/{}: {}", index, STAGE_NAMES.length, STAGE_NAMES[index - 1]);
    }

    @Scheduled(fixedDelayString = "${metrics.recompute-interval-ms}", initialDelay = 15_000)
    public void scheduledRecompute() {
        recomputeAll();
    }

    // Guards recomputeAll() so the 5-minute background schedule and the manual
    // /internal/recompute endpoint (a separate HTTP thread) can never run at the same time.
    // As data volume grew, a single recompute started taking longer than the schedule interval,
    // so every scheduled tick collided with whatever was already running — each one competing
    // for the same mart.metric_daily rows via ON CONFLICT, compounding into minutes-long queues.
    // tryLock() means a run that finds one already in progress is simply skipped (logged, not
    // queued) rather than piling up behind it: the next scheduled tick (or a fresh manual call)
    // will pick up any events the skipped run would have covered, since recompute is always a
    // full-window rebuild from staging, not incremental — skipping one cycle is harmless.
    private final ReentrantLock recomputeLock = new ReentrantLock();

    @Transactional
    public RecomputeResult recomputeAll() {
        if (!recomputeLock.tryLock()) {
            log.info("Recompute already in progress — skipping this trigger rather than queuing behind it");
            return null;
        }
        try {
            inProgress = true;
            runStartedAt = Instant.now();
            return doRecompute();
        } finally {
            inProgress = false;
            stageIndex = 0;
            recomputeLock.unlock();
        }
    }

    private RecomputeResult doRecompute() {
        long start = System.currentTimeMillis();
        enterStage(1);
        int deployments = jdbcTemplate.update(UPSERT.formatted(DEPLOYMENT_FREQUENCY), deployWorkflowPattern, windowDays);
        enterStage(2);
        int leadTime = jdbcTemplate.update(UPSERT.formatted(LEAD_TIME), deployWorkflowPattern, windowDays, windowDays);
        enterStage(3);
        int cfr = jdbcTemplate.update(UPSERT.formatted(CHANGE_FAILURE_RATE), deployWorkflowPattern, windowDays, hotfixWorkflowPattern);
        enterStage(4);
        int mttr = jdbcTemplate.update(UPSERT.formatted(MTTR), deployWorkflowPattern, windowDays, hotfixWorkflowPattern);
        enterStage(5);
        int velocity = jdbcTemplate.update(UPSERT.formatted(PR_VELOCITY), windowDays);
        enterStage(6);
        int cycleTime = jdbcTemplate.update(UPSERT.formatted(PR_CYCLE_TIME), windowDays);
        long elapsed = System.currentTimeMillis() - start;
        log.info("Mart recompute done in {} ms: {} deployment_frequency, {} lead_time, {} cfr, {} mttr, "
                        + "{} pr_velocity, {} pr_cycle_time rows",
                elapsed, deployments, leadTime, cfr, mttr, velocity, cycleTime);
        return new RecomputeResult(deployments, leadTime, cfr, mttr, velocity, cycleTime, elapsed);
    }

    public record RecomputeResult(int deploymentFrequencyRows, int leadTimeRows, int changeFailureRateRows,
                                  int mttrRows, int prVelocityRows, int prCycleTimeRows, long elapsedMs) {
    }
}