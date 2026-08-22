# Jenkins CI/CD Connector — Implementation Brief for Claude Code

## Context

This repo is **AI Impact Evaluation** (KPMG engineering-intelligence platform). It already has
working connectors for GitHub and Jira (`services/connectors/connector-github`,
`services/connectors/connector-jira`) feeding a shared event pipeline:

```
connector-* (publishes raw vendor JSON) → RabbitMQ → ingestion-writer's StagingEventWriter
  → staging.raw_event (source of truth) + typed "latest state" projection tables
  → api-core query services → frontend
```

**Goal**: add Jenkins as a second CI/CD source, alongside GitHub Actions, both writing into the
**same** `staging.workflow_run_state` table (repo, run_id, conclusion, name, ts,
last_received_at) — that table's schema was already designed provider-agnostic, so **no schema
migration is needed**, just a new connector module and new handling logic in
`StagingEventWriter`.

This brief contains everything needed to implement it — all file contents are final and were
already written and reasoned through in a prior session; nothing here needs to be re-derived or
re-designed. Just create/edit the files as specified.

## Verified facts (do not re-guess these — confirmed against a real local Jenkins instance)

A real Jenkins job (`aie-pipeline`, building `https://github.com/0pain01/AI_impact_analytical_program.git`)
was queried directly. Two real API responses:

**List builds** — `GET /job/{job}/api/json?tree=builds[number,result,timestamp,duration,url]`:
```json
{
  "builds": [
    {"number": 5, "result": "SUCCESS", "timestamp": 1787049648062, "duration": 10105, "url": "http://localhost:9090/job/aie-pipeline/5/"},
    {"number": 4, "result": "FAILURE", "timestamp": 1787048802467, "duration": 15170, "url": "..."}
  ]
}
```

**Single build with git info** — adding `actions[remoteUrls,lastBuiltRevision[SHA1,branch[name]]]`
to the tree param:
```json
{
  "actions": [
    {"_class": "hudson.model.CauseAction"},
    {"_class": "org.jenkinsci.plugins.workflow.libs.LibrariesAction"},
    {},
    {
      "_class": "hudson.plugins.git.util.BuildData",
      "lastBuiltRevision": {
        "SHA1": "a90f07cb8de8f7f277c1431cc1ae829aabf935d4",
        "branch": [{"name": "refs/remotes/origin/main"}]
      },
      "remoteUrls": ["https://github.com/0pain01/AI_impact_analytical_program.git"]
    },
    {}
  ],
  "duration": 10105, "number": 5, "result": "SUCCESS",
  "timestamp": 1787049648062, "url": "http://localhost:9090/job/aie-pipeline/5/"
}
```

Key takeaways baked into the code below:
- **`actions` is a mostly-empty-object array** — the repo URL is buried in whichever element has
  `"_class": "hudson.plugins.git.util.BuildData"`. Must scan for it, not assume position.
- **`timestamp` is epoch millis** (JSON number), not an ISO string.
- **`result` values are `SUCCESS`/`FAILURE`/`UNSTABLE`/`ABORTED`** (uppercase) — see the critical
  bug note below.
- Build numbers (`number`) only reset **per job**, not globally — two Jenkins jobs can both have
  a "build #5".

## Critical bug this design specifically avoids

`services/metrics-engine` hardcodes `conclusion = 'success'` (**lowercase**) in its DORA metric
queries (deployment frequency, change failure rate, MTTR). If Jenkins' `SUCCESS`/`FAILURE` were
stored verbatim, every Jenkins build would **silently** never match those queries — no error, just
wrong-looking numbers. The fix (in `StagingEventWriter` below) normalizes Jenkins' vocabulary to
the same lowercase one GitHub already uses: `SUCCESS→success`, `FAILURE`/`UNSTABLE→failure`,
`ABORTED→cancelled`. This means **zero changes needed to metrics-engine** — do not touch it.

## Port assignment

Existing ports: api-core=8080, connector-github=8081, ingestion-writer=8082, connector-jira=8083,
metrics-engine=8084, identity-service=8085. **connector-jenkins uses 8086** (next free).

## Step 1 — Register the new module

Edit `services/pom.xml`. Find the `<modules>` block and add connector-jenkins to it (it already
has a comment anticipating this exact addition):

```xml
  <modules>
    <module>platform-common</module>
    <module>api-core</module>
    <module>ingestion-writer</module>
    <module>metrics-engine</module>
    <module>identity-service</module>
    <module>connectors/connector-github</module>
    <module>connectors/connector-jira</module>
    <module>connectors/connector-jenkins</module>
  </modules>
```

## Step 2 — Create the connector-jenkins module

Create every file below at the exact path shown. This mirrors `connector-jira`'s module shape
exactly (same dependency set, same RabbitMQ config, same retry pattern) — do not restructure it
differently.

### `services/connectors/connector-jenkins/src/main/java/com/aiimpacteval/connector/jenkins/ConnectorJenkinsApplication.java`
```java
package com.aiimpacteval.connector.jenkins;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ConnectorJenkinsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConnectorJenkinsApplication.class, args);
    }
}
```

### `services/connectors/connector-jenkins/src/main/java/com/aiimpacteval/connector/jenkins/config/ClockConfig.java`
```java
package com.aiimpacteval.connector.jenkins.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

### `services/connectors/connector-jenkins/src/main/java/com/aiimpacteval/connector/jenkins/events/EventPublisher.java`
```java
package com.aiimpacteval.connector.jenkins.events;

import com.aiimpacteval.common.events.EventEnvelope;

/** Port for publishing connector events; RabbitMQ in production, recording stub in tests. */
public interface EventPublisher {

    void publish(EventEnvelope envelope);
}
```

### `services/connectors/connector-jenkins/src/main/java/com/aiimpacteval/connector/jenkins/events/RabbitEventPublisher.java`
```java
package com.aiimpacteval.connector.jenkins.events;

import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.common.events.EventTopology;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitEventPublisher implements EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(EventEnvelope envelope) {
        rabbitTemplate.convertAndSend(EventTopology.EVENTS_EXCHANGE, envelope.routingKey(), envelope);
    }
}
```

### `services/connectors/connector-jenkins/src/main/java/com/aiimpacteval/connector/jenkins/events/RabbitConfig.java`
```java
package com.aiimpacteval.connector.jenkins.events;

import com.aiimpacteval.common.events.EventTopology;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    TopicExchange eventsExchange() {
        return ExchangeBuilder.topicExchange(EventTopology.EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                  Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
```

### `services/connectors/connector-jenkins/src/main/java/com/aiimpacteval/connector/jenkins/backfill/JenkinsBackfillService.java`
```java
package com.aiimpacteval.connector.jenkins.backfill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.connector.jenkins.events.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Backfills build history for one Jenkins job (PRD E1-S3, alt. CI/CD source alongside
 * connector-github — see system-architecture.md's "CI/CD (GH Actions / Jenkins)" boundary).
 * Idempotency via ADR-0003: {@code sourceId} is {@code jenkins:{jobName}:{buildNumber}}, unique
 * per job since Jenkins build numbers only reset per-job, not globally.
 *
 * <p>Repo attribution reads the {@code hudson.plugins.git.util.BuildData} entry inside each
 * build's {@code actions} array (verified against a real local Jenkins instance — see chat, not
 * assumed from docs) — {@code remoteUrls[0]} gives the git URL, normalized here into the same
 * {@code owner/repo} shape used everywhere else in this system. A build with no Git plugin data
 * (e.g. a freestyle job with no SCM configured) is published with {@code repo = "unknown"}
 * rather than being silently dropped.
 *
 * <p>No incremental "since" cursor (unlike GitHub/Jira) — Jenkins' own build retention already
 * bounds job history, and this fetches whatever the job API returns in one page. A job with an
 * unusually large build history may need a bounded {@code tree=builds[0,N]{...}} query added
 * later; not built now since it wasn't verified against a real large-history job.
 */
@Service
public class JenkinsBackfillService {

    static final String CONNECTOR_VERSION = "0.1.0";
    private static final Logger log = LoggerFactory.getLogger(JenkinsBackfillService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final RestClient restClient;
    private final EventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JenkinsBackfillService(RestClient.Builder restClientBuilder,
                                  EventPublisher publisher,
                                  ObjectMapper objectMapper,
                                  Clock clock,
                                  @Value("${jenkins.base-url}") String baseUrl,
                                  @Value("${jenkins.username}") String username,
                                  @Value("${jenkins.api-token}") String apiToken) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl == null || baseUrl.isBlank() ? "http://unconfigured.invalid" : baseUrl)
                .defaultHeaders(headers -> {
                    if (username != null && !username.isBlank()) {
                        headers.setBasicAuth(username, apiToken);
                    }
                })
                .defaultHeader("Accept", "application/json")
                .build();
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public BackfillResult backfillJob(String jobName) {
        JsonNode response = fetchPage(() -> restClient.get()
                .uri("/job/{job}/api/json?tree=builds[number,result,timestamp,duration,url,actions[remoteUrls,lastBuiltRevision[SHA1,branch[name]]]]",
                        jobName)
                .retrieve()
                .body(String.class));

        JsonNode builds = response == null ? null : response.get("builds");
        int published = 0;
        if (builds != null && builds.isArray()) {
            for (JsonNode build : builds) {
                // Job name isn't on the per-build object — attach it so downstream (ingestion-
                // writer) can key sourceId/run_id without needing separate connector context.
                ((ObjectNode) build).put("job_name", jobName);

                String number = textOrNull(build, "number");
                if (number == null) {
                    continue;
                }
                publisher.publish(new EventEnvelope(
                        "jenkins",
                        "jenkins:" + jobName + ":" + number,
                        "build.snapshot",
                        Instant.now(clock),
                        CONNECTOR_VERSION,
                        build));
                published++;
            }
        }

        log.info("Backfill job {} complete: {} builds", jobName, published);
        return new BackfillResult(published);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private JsonNode fetchPage(Supplier<String> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                String body = call.get();
                return body == null ? null : objectMapper.readTree(body);
            } catch (Exception e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new BackfillException("Jenkins API call failed after " + MAX_ATTEMPTS + " attempts", e);
                }
                long backoffMillis = Duration.ofSeconds(2L * attempt).toMillis();
                log.warn("Jenkins API call failed (attempt {}/{}), retrying in {} ms: {}",
                        attempt, MAX_ATTEMPTS, backoffMillis, e.getMessage());
                try {
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new BackfillException("Backfill interrupted", interrupted);
                }
            }
        }
    }

    public record BackfillResult(int builds) {
    }

    public static class BackfillException extends RuntimeException {
        public BackfillException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
```

### `services/connectors/connector-jenkins/src/main/java/com/aiimpacteval/connector/jenkins/backfill/BackfillController.java`
```java
package com.aiimpacteval.connector.jenkins.backfill;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal trigger, not exposed publicly. Invoked by api-core when a Jenkins job is connected;
 * direct call supports pilot onboarding and debugging (same shape as connector-jira's).
 */
@RestController
public class BackfillController {

    private final JenkinsBackfillService backfillService;

    public BackfillController(JenkinsBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @PostMapping("/internal/backfill")
    public JenkinsBackfillService.BackfillResult backfill(@RequestParam String jobName) {
        return backfillService.backfillJob(jobName);
    }
}
```

### `services/connectors/connector-jenkins/pom.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.aiimpacteval</groupId>
    <artifactId>ai-impact-evaluation-services</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>

  <artifactId>connector-jenkins</artifactId>
  <name>AI Impact Evaluation Connector - Jenkins</name>
  <description>Backfills Jenkins build history (CI/CD, PRD E1-S3 alt. source); publishes raw events (ADR-0002/0003)</description>

  <dependencies>
    <dependency>
      <groupId>com.aiimpacteval</groupId>
      <artifactId>platform-common</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-amqp</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

### `services/connectors/connector-jenkins/src/main/resources/application.yml`
```yaml
spring:
  application:
    name: connector-jenkins
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:aiimpacteval}
    password: ${RABBITMQ_PASSWORD:aiimpacteval_local}
    publisher-confirm-type: correlated

server:
  port: ${SERVER_PORT:8086}

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      probes:
        enabled: true

jenkins:
  base-url: ${JENKINS_BASE_URL:}
  username: ${JENKINS_USERNAME:}
  # Jenkins API token (user profile -> Configure -> API Token), used as the Basic-auth password.
  api-token: ${JENKINS_API_TOKEN:}
```

## Step 3 — Update StagingEventWriter.java (existing file, full replacement)

This is `services/ingestion-writer/src/main/java/com/aiimpacteval/ingestion/StagingEventWriter.java`.
It already has GitHub and Jira handling — this version adds Jenkins handling on top, unchanged
otherwise. **Replace the entire file** with the content below rather than trying to hand-merge —
it's already the complete, correct, final version including the existing GitHub/Jira logic.

Read the current file first to confirm it matches what you'd expect (GitHub workflow_run/PR/PR
review handling + Jira issue handling already present) before overwriting, in case it's drifted
from what's described here — if it has diverged significantly, stop and flag that rather than
silently discarding unrelated changes.

```java
package com.aiimpacteval.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.common.events.EventTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Persists every connector event into the immutable staging store.
 *
 * <p>Idempotent by the {@code (source, source_id, event_type)} natural key (ADR-0003):
 * redeliveries and replays are no-ops. Unrecoverable messages are rejected without requeue,
 * which routes them to the DLQ — never silently dropped (FR-1.8).
 *
 * <p>Also maintains {@code staging.workflow_run_state} / {@code staging.pull_request_state} /
 * {@code staging.pull_request_review_state} / {@code staging.jira_issue_state} — typed, indexed
 * "latest known state" projections of the corresponding snapshot/webhook events. {@code
 * staging.raw_event} stays the source of truth; these projections exist purely so query
 * services don't have to re-derive "latest state per run/PR/issue" from JSONB via DISTINCT ON on
 * every request (see V5 migration for the one-time backfill of pre-existing events, and the perf
 * history that motivated this). V10 added {@code jira_issue_state} — connector-jira had been
 * publishing issue events since it was built, but nothing ever read them back out until
 * Investment Profile needed to classify git activity against Jira issue types.
 *
 * <p>{@code workflow_run_state} is fed by two independent sources now: GitHub Actions (via
 * connector-github) and Jenkins (via connector-jenkins, PRD E1-S3's alt. CI/CD source). Both
 * write into the same table/columns — the schema was already provider-agnostic (repo/run_id/
 * conclusion/name/ts, nothing GitHub-specific). The one thing that had to be handled carefully:
 * metrics-engine's DORA queries hardcode {@code conclusion = 'success'} (lowercase), but Jenkins
 * reports {@code SUCCESS}/{@code FAILURE}/{@code UNSTABLE}/{@code ABORTED} — stored verbatim,
 * every Jenkins build would have silently vanished from deployment-frequency/MTTR/CFR metrics
 * with no error. {@link #normalizeJenkinsResult} maps Jenkins' vocabulary onto the same lowercase
 * one GitHub already uses, so metrics-engine needed zero changes.
 */
@Component
public class StagingEventWriter {

    private static final Logger log = LoggerFactory.getLogger(StagingEventWriter.class);

    private static final String INSERT_SQL = """
            INSERT INTO staging.raw_event (source, source_id, event_type, received_at, connector_version, payload)
            VALUES (?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT ON CONSTRAINT uq_raw_event_natural_key DO NOTHING
            """;

    // last_received_at guard: if events ever arrive out of order (retry/requeue), an older
    // snapshot can never clobber a newer one that already landed — matches the "latest by
    // received_at wins" semantics the old DISTINCT ON ... ORDER BY received_at DESC query used.
    private static final String UPSERT_WORKFLOW_RUN_SQL = """
            INSERT INTO staging.workflow_run_state (repo, run_id, conclusion, name, ts, last_received_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (repo, run_id) DO UPDATE SET
                conclusion = EXCLUDED.conclusion, name = EXCLUDED.name, ts = EXCLUDED.ts,
                last_received_at = EXCLUDED.last_received_at
            WHERE EXCLUDED.last_received_at > staging.workflow_run_state.last_received_at
            """;

    private static final String UPSERT_PULL_REQUEST_SQL = """
            INSERT INTO staging.pull_request_state
                (repo, pr_id, number, title, author, html_url, state, requested_reviewers,
                 created_at, merged_at, last_received_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (repo, pr_id) DO UPDATE SET
                number = EXCLUDED.number, title = EXCLUDED.title, author = EXCLUDED.author,
                html_url = EXCLUDED.html_url, state = EXCLUDED.state,
                requested_reviewers = EXCLUDED.requested_reviewers,
                created_at = EXCLUDED.created_at, merged_at = EXCLUDED.merged_at,
                last_received_at = EXCLUDED.last_received_at
            WHERE EXCLUDED.last_received_at > staging.pull_request_state.last_received_at
            """;

    // Reviews can be dismissed after submission (state changes to DISMISSED), so this is a
    // guarded upsert like the others, not an insert-once.
    private static final String UPSERT_PULL_REQUEST_REVIEW_SQL = """
            INSERT INTO staging.pull_request_review_state
                (repo, pr_number, review_id, reviewer_login, state, submitted_at, last_received_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (repo, review_id) DO UPDATE SET
                reviewer_login = EXCLUDED.reviewer_login, state = EXCLUDED.state,
                submitted_at = EXCLUDED.submitted_at, last_received_at = EXCLUDED.last_received_at
            WHERE EXCLUDED.last_received_at > staging.pull_request_review_state.last_received_at
            """;

    // reopened is sticky (OR-merged, never cleared) because a webhook update event only carries
    // the changelog delta for that one change, not full history — only backfill's
    // expand=changelog scan sees the whole history at once. See wasReopened().
    private static final String UPSERT_JIRA_ISSUE_SQL = """
            INSERT INTO staging.jira_issue_state
                (issue_key, issue_id, project_key, issue_type, status, summary, assignee,
                 created_at, resolved_at, reopened, last_received_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (issue_key) DO UPDATE SET
                issue_id = EXCLUDED.issue_id, project_key = EXCLUDED.project_key,
                issue_type = EXCLUDED.issue_type, status = EXCLUDED.status,
                summary = EXCLUDED.summary, assignee = EXCLUDED.assignee,
                created_at = EXCLUDED.created_at, resolved_at = EXCLUDED.resolved_at,
                reopened = staging.jira_issue_state.reopened OR EXCLUDED.reopened,
                last_received_at = EXCLUDED.last_received_at
            WHERE EXCLUDED.last_received_at > staging.jira_issue_state.last_received_at
            """;

    private static final Set<String> WORKFLOW_RUN_EVENT_TYPES = Set.of("workflow_run", "workflow_run.snapshot");
    private static final Set<String> PULL_REQUEST_EVENT_TYPES = Set.of("pull_request", "pull_request.snapshot");
    private static final Set<String> PULL_REQUEST_REVIEW_EVENT_TYPES =
            Set.of("pull_request_review", "pull_request_review.snapshot");
    // jira:issue_deleted is deliberately not in this set — no reliable fields left to project,
    // and dropping the row on delete isn't worth the added complexity for a rare event.
    private static final Set<String> JIRA_ISSUE_EVENT_TYPES =
            Set.of("issue.snapshot", "jira:issue_created", "jira:issue_updated");
    private static final Set<String> TERMINAL_STATUS_NAMES = Set.of("done", "closed", "resolved");
    private static final Set<String> JENKINS_BUILD_EVENT_TYPES = Set.of("build.snapshot");
    private static final String JENKINS_GIT_BUILD_DATA_CLASS = "hudson.plugins.git.util.BuildData";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public StagingEventWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = EventTopology.STAGING_QUEUE)
    public void onEvent(EventEnvelope envelope) {
        write(envelope);
    }

    /** @return true if a new row was written, false if it was a duplicate (idempotent skip). */
    public boolean write(EventEnvelope envelope) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(envelope.payload());
        } catch (JsonProcessingException e) {
            // Malformed beyond repair — reject without requeue so it lands in the DLQ.
            throw new AmqpRejectAndDontRequeueException("Unserializable payload for " + envelope.sourceId(), e);
        }

        int inserted = jdbcTemplate.update(INSERT_SQL,
                envelope.source(),
                envelope.sourceId(),
                envelope.eventType(),
                Timestamp.from(envelope.receivedAt()),
                envelope.connectorVersion(),
                payloadJson);

        if (inserted == 0) {
            log.debug("Duplicate event skipped: {}/{}/{}",
                    envelope.source(), envelope.sourceId(), envelope.eventType());
            return false;
        }

        if ("github".equals(envelope.source())) {
            if (WORKFLOW_RUN_EVENT_TYPES.contains(envelope.eventType())) {
                upsertWorkflowRunState(envelope);
            } else if (PULL_REQUEST_EVENT_TYPES.contains(envelope.eventType())) {
                upsertPullRequestState(envelope);
            } else if (PULL_REQUEST_REVIEW_EVENT_TYPES.contains(envelope.eventType())) {
                upsertPullRequestReviewState(envelope);
            }
        } else if ("jira".equals(envelope.source()) && JIRA_ISSUE_EVENT_TYPES.contains(envelope.eventType())) {
            upsertJiraIssueState(envelope);
        } else if ("jenkins".equals(envelope.source()) && JENKINS_BUILD_EVENT_TYPES.contains(envelope.eventType())) {
            upsertJenkinsBuildState(envelope);
        }
        return true;
    }

    private void upsertWorkflowRunState(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        JsonNode run = payload.has("workflow_run") ? payload.get("workflow_run") : payload;

        String runId = textOrNull(run, "id");
        if (runId == null) {
            return; // nothing to key the projection on — skip rather than guess
        }
        String repo = firstNonBlank(
                textAtPath(run, "repository", "full_name"),
                textAtPath(payload, "repository", "full_name"),
                "unknown");
        String conclusion = textOrNull(run, "conclusion");
        String name = textOrNull(run, "name");
        Instant ts = instantOrNull(textOrNull(run, "updated_at"));

        jdbcTemplate.update(UPSERT_WORKFLOW_RUN_SQL,
                repo, runId, conclusion, name,
                ts == null ? null : Timestamp.from(ts),
                Timestamp.from(envelope.receivedAt()));
    }

    private void upsertPullRequestState(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        JsonNode pr = payload.has("pull_request") ? payload.get("pull_request") : payload;

        String prId = textOrNull(pr, "id");
        if (prId == null) {
            return;
        }
        String repo = firstNonBlank(textAtPath(pr, "base", "repo", "full_name"), "unknown");
        Long number = longOrNull(textOrNull(pr, "number"));
        String title = textOrNull(pr, "title");
        String author = textAtPath(pr, "user", "login");
        String htmlUrl = textOrNull(pr, "html_url");
        String state = textOrNull(pr, "state");
        String[] requestedReviewers = extractLogins(pr.get("requested_reviewers"));
        Instant createdAt = instantOrNull(textOrNull(pr, "created_at"));
        Instant mergedAt = instantOrNull(textOrNull(pr, "merged_at"));

        // Plain jdbcTemplate.update(...) can't portably bind a text[] parameter, so this one
        // needs a PreparedStatementCreator to call Connection.createArrayOf ourselves.
        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement(UPSERT_PULL_REQUEST_SQL);
            ps.setString(1, repo);
            ps.setString(2, prId);
            if (number == null) {
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(3, number);
            }
            ps.setString(4, title);
            ps.setString(5, author);
            ps.setString(6, htmlUrl);
            ps.setString(7, state);
            ps.setArray(8, con.createArrayOf("text", requestedReviewers));
            ps.setTimestamp(9, createdAt == null ? null : Timestamp.from(createdAt));
            ps.setTimestamp(10, mergedAt == null ? null : Timestamp.from(mergedAt));
            ps.setTimestamp(11, Timestamp.from(envelope.receivedAt()));
            return ps;
        });
    }

    private void upsertPullRequestReviewState(EventEnvelope envelope) {
        JsonNode review = envelope.payload();

        String reviewId = textOrNull(review, "id");
        String prUrl = textOrNull(review, "pull_request_url");
        if (reviewId == null || prUrl == null) {
            return;
        }
        RepoAndPrNumber location = parsePullRequestUrl(prUrl);
        if (location == null) {
            log.warn("Could not parse repo/PR number from pull_request_url '{}' — skipping review {}",
                    prUrl, reviewId);
            return;
        }
        String reviewerLogin = textAtPath(review, "user", "login");
        String state = textOrNull(review, "state");
        Instant submittedAt = instantOrNull(textOrNull(review, "submitted_at"));

        jdbcTemplate.update(UPSERT_PULL_REQUEST_REVIEW_SQL,
                location.repo(), location.prNumber(), reviewId, reviewerLogin, state,
                submittedAt == null ? null : Timestamp.from(submittedAt),
                Timestamp.from(envelope.receivedAt()));
    }

    private void upsertJiraIssueState(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        // Backfill (issue.snapshot): payload IS the issue object. Webhook (jira:issue_created/
        // _updated): payload wraps it under "issue", with "changelog" as a sibling, not nested.
        JsonNode issue = payload.has("issue") ? payload.get("issue") : payload;
        JsonNode fields = issue.get("fields");
        String issueKey = textOrNull(issue, "key");
        String issueId = textOrNull(issue, "id");
        if (issueKey == null || issueId == null || fields == null) {
            return;
        }
        String projectKey = firstNonBlank(textAtPath(fields, "project", "key"), "unknown");
        String issueType = textAtPath(fields, "issuetype", "name");
        String status = textAtPath(fields, "status", "name");
        String summary = textOrNull(fields, "summary");
        String assignee = textAtPath(fields, "assignee", "displayName");
        Instant createdAt = jiraInstantOrNull(textOrNull(fields, "created"));
        Instant resolvedAt = jiraInstantOrNull(textOrNull(fields, "resolutiondate"));

        JsonNode changelog = payload.has("changelog") ? payload.get("changelog") : issue.get("changelog");
        boolean reopened = wasReopened(changelog);

        jdbcTemplate.update(UPSERT_JIRA_ISSUE_SQL,
                issueKey, issueId, projectKey, issueType, status, summary, assignee,
                createdAt == null ? null : Timestamp.from(createdAt),
                resolvedAt == null ? null : Timestamp.from(resolvedAt),
                reopened,
                Timestamp.from(envelope.receivedAt()));
    }

    /**
     * Heuristic, not authoritative (see V10 migration comment): flags a status transition away
     * from one of Jira's default terminal status names (Done/Closed/Resolved). Projects on
     * custom workflows with differently-named terminal statuses won't be caught — this
     * undercounts rework rather than overcounting it, which is the safer direction to be wrong
     * in for a metric people will make decisions from.
     */
    private static boolean wasReopened(JsonNode changelog) {
        if (changelog == null) {
            return false;
        }
        JsonNode histories = changelog.get("histories");
        if (histories != null && histories.isArray()) {
            // Backfill's expand=changelog: full history, one entry per past change.
            for (JsonNode history : histories) {
                if (statusItemsShowReopen(history.get("items"))) {
                    return true;
                }
            }
            return false;
        }
        // Webhook update delta: a single changelog entry shaped {"items": [...]}, no wrapper.
        return statusItemsShowReopen(changelog.get("items"));
    }

    private static boolean statusItemsShowReopen(JsonNode items) {
        if (items == null || !items.isArray()) {
            return false;
        }
        for (JsonNode item : items) {
            if ("status".equals(textOrNull(item, "field"))) {
                String from = textOrNull(item, "fromString");
                if (from != null && TERMINAL_STATUS_NAMES.contains(from.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void upsertJenkinsBuildState(EventEnvelope envelope) {
        JsonNode build = envelope.payload();

        String buildNumber = textOrNull(build, "number");
        String jobName = textOrNull(build, "job_name");
        if (buildNumber == null || jobName == null) {
            return;
        }
        // Build numbers only reset per-job in Jenkins, not globally — two different jobs could
        // both have a "build #5". Namespacing by job here keeps (repo, run_id) unique.
        String runId = "jenkins:" + jobName + ":" + buildNumber;

        String repo = extractJenkinsRepo(build.get("actions"));
        String conclusion = normalizeJenkinsResult(textOrNull(build, "result"));
        Long timestampMillis = longOrNull(build, "timestamp");
        Instant ts = timestampMillis == null ? null : Instant.ofEpochMilli(timestampMillis);

        jdbcTemplate.update(UPSERT_WORKFLOW_RUN_SQL,
                repo == null ? "unknown" : repo,
                runId,
                conclusion,
                jobName,
                ts == null ? null : Timestamp.from(ts),
                Timestamp.from(envelope.receivedAt()));
    }

    /**
     * Jenkins doesn't put the git repo on the build object directly — it's inside a
     * {@code hudson.plugins.git.util.BuildData} entry in the (otherwise mostly-empty-object)
     * {@code actions} array. Verified against a real local Jenkins instance, not assumed from
     * docs — the shape genuinely is this scattered.
     */
    private static String extractJenkinsRepo(JsonNode actions) {
        if (actions == null || !actions.isArray()) {
            return null;
        }
        for (JsonNode action : actions) {
            if (!JENKINS_GIT_BUILD_DATA_CLASS.equals(textOrNull(action, "_class"))) {
                continue;
            }
            JsonNode remoteUrls = action.get("remoteUrls");
            if (remoteUrls != null && remoteUrls.isArray() && !remoteUrls.isEmpty()) {
                return normalizeGitUrl(remoteUrls.get(0).asText());
            }
        }
        return null;
    }

    // "https://github.com/0pain01/AI_impact_analytical_program.git" -> "0pain01/AI_impact_analytical_program"
    // Falls back to returning the trimmed URL as-is for non-GitHub git hosts rather than
    // dropping the data — better an unfamiliar-looking repo value than a silently missing one.
    private static String normalizeGitUrl(String url) {
        String trimmed = url.trim();
        if (trimmed.endsWith(".git")) {
            trimmed = trimmed.substring(0, trimmed.length() - 4);
        }
        int idx = trimmed.indexOf("github.com/");
        return idx >= 0 ? trimmed.substring(idx + "github.com/".length()) : trimmed;
    }

    // metrics-engine's DORA queries hardcode `conclusion = 'success'` (lowercase) — Jenkins
    // reports SUCCESS/FAILURE/UNSTABLE/ABORTED. Without this mapping, every Jenkins build would
    // silently never match those queries; see class javadoc.
    private static String normalizeJenkinsResult(String rawResult) {
        if (rawResult == null) {
            return null; // still building — no result yet, same as an in-progress GitHub run
        }
        return switch (rawResult) {
            case "SUCCESS" -> "success";
            case "FAILURE", "UNSTABLE" -> "failure";
            case "ABORTED" -> "cancelled";
            default -> rawResult.toLowerCase(Locale.ROOT);
        };
    }

    private static Long longOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asLong();
    }

    private record RepoAndPrNumber(String repo, long prNumber) {
    }

    // e.g. "https://api.github.com/repos/expressjs/express/pulls/7369" -> ("expressjs/express", 7369)
    private static RepoAndPrNumber parsePullRequestUrl(String url) {
        int reposIdx = url.indexOf("/repos/");
        int pullsIdx = url.indexOf("/pulls/");
        if (reposIdx < 0 || pullsIdx < 0 || pullsIdx <= reposIdx) {
            return null;
        }
        String repo = url.substring(reposIdx + "/repos/".length(), pullsIdx);
        String numberStr = url.substring(pullsIdx + "/pulls/".length());
        try {
            return new RepoAndPrNumber(repo, Long.parseLong(numberStr));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String textAtPath(JsonNode node, String... path) {
        JsonNode cur = node;
        for (String p : path) {
            if (cur == null || cur.isNull()) {
                return null;
            }
            cur = cur.get(p);
        }
        return cur == null || cur.isNull() ? null : cur.asText();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static Instant instantOrNull(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            log.warn("Unparseable timestamp '{}' — leaving null", iso);
            return null;
        }
    }

    // Jira sends "2024-01-15T10:30:00.000+0000" (no colon in the offset) rather than GitHub's
    // proper ISO-8601 "...Z" — Instant.parse rejects that format outright, so this tries it
    // first (harmless if Jira ever does send a real 'Z'/colon offset) and falls back to Jira's
    // actual format rather than silently losing the date on every single issue.
    private static final DateTimeFormatter JIRA_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private static Instant jiraInstantOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception isoFailed) {
            try {
                return OffsetDateTime.parse(raw, JIRA_TIMESTAMP_FORMAT).toInstant();
            } catch (Exception e) {
                log.warn("Unparseable Jira timestamp '{}' — leaving null", raw);
                return null;
            }
        }
    }

    private static Long longOrNull(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // requested_reviewers is an array of GitHub user objects — we only need their logins.
    private static String[] extractLogins(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return new String[0];
        }
        List<String> logins = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            String login = textOrNull(item, "login");
            if (login != null) {
                logins.add(login);
            }
        }
        return logins.toArray(new String[0]);
    }
}
```

## Step 4 — Build and run

```powershell
mvnw install -pl services/connectors/connector-jenkins -am -DskipTests
```

Run `ConnectorJenkinsApplication` with these environment variables (adjust to your real values):

```
JENKINS_BASE_URL=http://localhost:9090
JENKINS_USERNAME=<your Jenkins username>
JENKINS_API_TOKEN=<your Jenkins API token, from user profile → Configure → API Token>
```

RabbitMQ must already be running (the other connectors depend on it too, so it likely already is).
Confirm the console shows `Started ConnectorJenkinsApplication` with no errors, listening on 8086.

Restart `ingestion-writer` too, since `StagingEventWriter.java` changed.

## Step 5 — Verify end-to-end

There's already a real Jenkins job set up locally: job name `aie-pipeline`, building
`0pain01/AI_impact_analytical_program`, with a working `Jenkinsfile` at the repo root and both
SUCCESS and FAILURE builds in its history already.

```powershell
Invoke-RestMethod -Method POST -Uri "http://localhost:8086/internal/backfill?jobName=aie-pipeline"
```

Expect `{"builds": 5}` (or however many builds exist by the time this runs). Then check directly
in Postgres:

```powershell
docker exec ai-impact-evaluation-postgres-1 psql -U aiimpacteval -d aiimpacteval -c "SELECT repo, run_id, conclusion, name FROM staging.workflow_run_state WHERE run_id LIKE 'jenkins:%';"
```

Expect 5 rows, `repo = '0pain01/AI_impact_analytical_program'`, `conclusion` values lowercase
(`success`/`failure`, NOT `SUCCESS`/`FAILURE`) — that lowercase check is the single most important
thing to verify; getting it wrong silently breaks Cockpit's DORA metrics for Jenkins builds with
no visible error.

Then check the Admin console (`/api/v1/admin/connectors`, or the Admin tab in the UI) — there's no
explicit "Jenkins" row wired into `AdminConnectorService` yet (it currently only reports GitHub /
GitHub Actions / Jira) — adding one is a reasonable next step but wasn't done in this brief; check
`services/api-core/src/main/java/com/aiimpacteval/apicore/admin/AdminConnectorService.java` for
the pattern to extend if the person wants that visibility too.

## Known gaps — do not silently "fix" these without flagging them to the person first

- **No automated test written yet** for the Jenkins-handling logic in `StagingEventWriter`. There's
  an existing `StagingEventWriterIntegrationTest` in `services/ingestion-writer/src/test/java/...`
  that should probably be extended with Jenkins cases (a SUCCESS build, a FAILURE build, a build
  with no `BuildData` action at all to confirm the `repo = "unknown"` fallback) — this was flagged
  as a real gap, not an oversight to quietly leave.
- **No incremental "since" cursor** — `backfillJob` fetches whatever Jenkins' build list API
  returns in one page every time it's called (bounded by Jenkins' own build-retention settings,
  not by us). Fine for a handful of builds; would need `tree=builds[0,N]{...}` range pagination
  added if a job has a very large build history. Not built because it was never verified against
  a real large-history job — don't add speculative pagination logic without a real case to test it
  against.
- **Freestyle Jenkins jobs with no Git plugin configured** will have no `BuildData` action at all
  → `repo` ends up `"unknown"` rather than being dropped. This is intentional (see
  `extractJenkinsRepo`'s javadoc) but means such builds won't correlate with anything else in the
  system (PRs, teams, etc.) — expected, not a bug to "fix."
- **No webhook support** — this is backfill/polling-only, matching how connector-jira was tested
  locally. A real production deployment would likely want Jenkins' Generic Webhook Trigger plugin
  wired to push events instead of polling; that's a larger, separate piece of work not attempted
  here.

## What NOT to change

- `services/metrics-engine` — the lowercase-conclusion normalization in `StagingEventWriter`
  exists specifically so metrics-engine needs zero changes. Do not "helpfully" add Jenkins-specific
  handling there.
- The `staging.workflow_run_state` schema — it's already correct and provider-agnostic. No
  migration needed for this feature.
- Any file not listed above.
