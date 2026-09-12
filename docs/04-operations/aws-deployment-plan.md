# AWS Deployment Plan — Small Team (≤ 20 developers), Cost-Optimized

This is a concrete, sized deployment plan for running AI Impact Evaluation on AWS for an
organization with **10–20 developers** (i.e. 10–20 people whose activity is being measured — the
actual number of people *logging into the dashboards* is typically much smaller: a handful of
managers, leads, and admins). It complements the platform-agnostic
[deployment-guide.md](deployment-guide.md) — read that first for what each service does and the
full env-var/secret reference; this document is the AWS-specific "which service, what size, what
it costs, and in what order" answer.

## 1. Sizing assumptions

At 10–20 developers across, realistically, 10–30 connected repositories:

- **Event volume:** low. A handful of PRs/MRs, commits, and CI runs per developer per day —
  perhaps 200–1,000 raw events/day at the high end. This is nowhere near a scale that needs
  horizontal auto-scaling, sharding, or a managed Kafka cluster.
- **Dashboard traffic:** a handful of concurrent viewers at peak (managers checking Cockpit,
  maybe once or twice a day per person). Single-task-per-service, no load balancing *within* a
  service, is entirely sufficient.
- **Data retention:** the audit log needs 12+ months (BRD requirement); `staging.raw_event` is
  meant to be kept indefinitely as the replay/audit source of truth. At this event volume, 12
  months of data is easily tens of megabytes, not a storage planning concern.
- **Availability expectations:** this is an internal engineering-intelligence tool, not a
  customer-facing product with an SLA. Single-AZ, no cross-region failover, and brief planned
  downtime for a deploy are all reasonable trade-offs at this scale — call this out explicitly to
  whoever approves the deployment, and revisit if that risk tolerance changes.

## 2. Two deployment tiers — pick one

| | **Tier 1 — Single EC2 instance** | **Tier 2 — Managed services (recommended)** |
|---|---|---|
| **What** | One EC2 instance running the exact same `docker-compose` + `start-backend.sh` setup used locally | RDS for Postgres, Amazon MQ (or self-hosted RabbitMQ) for the queue, ECS Fargate for all 9 backend services, S3+CloudFront for the frontend |
| **Est. monthly cost** | **~$45–70** | **~$120–150** |
| **Pros** | Cheapest possible; fastest to stand up; literally the local setup moved to a box | Automated backups/patching (RDS), service isolation (one service crashing doesn't take down the database), independent scaling/redeploy per service, no single point of failure for infra |
| **Cons** | No service isolation — one bad deploy or memory leak affects everything; manual OS patching/backups; scaling means resizing the whole box | More moving pieces to set up initially |
| **Good for** | A quick pilot / proof-of-concept, or a genuinely cost-constrained deployment | Anything meant to run for more than a few months, or that might grow past 20 developers |

**Recommendation: Tier 2.** The cost delta (~$70–80/month) is small in absolute terms, and the
architecture's core value proposition — connectors isolated so a vendor outage never loses data
— is meaningfully weaker on a single box where a connector's own crash-loop can starve the CPU
every other service needs. Tier 1 is documented below too, for a genuine pilot/budget-constrained
case.

## 3. AWS service mapping (Tier 2)

| Platform component | AWS service | Recommended size | Why this size |
|---|---|---|---|
| PostgreSQL (`staging`/`core`/`mart`) | **RDS for PostgreSQL 16** | `db.t4g.micro` (2 vCPU burstable, 1 GB RAM), single-AZ, 20 GB gp3 storage | Graviton (`t4g`) is ~20% cheaper than `t3` for the same burstable performance; at this event volume the workload is almost entirely small, frequent writes/reads — burstable credits comfortably cover it. Single-AZ is the cost-conscious choice here (see §1) |
| Message queue | **Amazon MQ for RabbitMQ**, single-instance broker | `mq.t3.micro` | Managed — no patching/HA config to hand-roll. The cheaper alternative is self-hosting RabbitMQ as a 10th Fargate task with an EFS-backed data volume for persistence, saving roughly $15–20/month at the cost of owning its upgrades yourself; reasonable either way at this scale |
| The 9 backend services | **ECS Fargate**, one task each | `0.25 vCPU / 0.5 GB` per task (Fargate's minimum) for `api-core`, `metrics-engine`, `identity-service`, `ingestion-writer`; same size on **Fargate Spot** for the 5 connectors | The connectors are explicitly designed to tolerate interruption — a connector restarting loses nothing, since RabbitMQ buffers and every write downstream is idempotent (§3/§8 of the technical spec) — so Spot's ~70% discount is free money for them. `api-core`/`ingestion-writer`/`identity-service`/`metrics-engine` stay on standard Fargate since they're either user-facing or one of the two "hear everything" consumers |
| Frontend | **S3 (static hosting) + CloudFront** | — | The frontend is a static build (`npm run build`) with no server runtime — this is by far the cheapest and lowest-maintenance way to serve it, and gets TLS + a CDN for free |
| Container images | **ECR** (one repository per service) | — | |
| Ingress / routing to the backend services | **Application Load Balancer** | One ALB, path- or host-based routing to each service's Fargate target group | A single ALB in front of everything is enough at this traffic level — no need for one ALB per service |
| Secrets (`GITHUB_TOKEN`, `GITLAB_TOKEN`, `JIRA_API_TOKEN`, `JENKINS_API_TOKEN`, DB/MQ credentials) | **Secrets Manager** | ~10 secrets | Injected into Fargate task definitions as secret references, never as plain task-definition environment values |
| TLS certificates | **ACM** | One cert covering the app domain (and its API subdomain, if split) | Free, auto-renewing, integrates directly with the ALB and CloudFront |
| DNS | **Route 53** | One hosted zone | |
| Logs / basic monitoring | **CloudWatch Logs** + **Container Insights** | | Every service's stdout already goes to CloudWatch automatically via the Fargate log driver — no extra agent to install |
| Networking | **VPC**: 2 public subnets (ALB, NAT gateway) + 2 private subnets (Fargate tasks, RDS, Amazon MQ) across 2 AZs | | Standard shape; RDS/Amazon MQ/Fargate tasks have no business being in a public subnet |

## 4. Estimated monthly cost (Tier 2, `us-east-1`, on-demand pricing)

| Item | Est. monthly cost |
|---|---|
| RDS `db.t4g.micro`, single-AZ, 20 GB gp3 | ~$18 |
| Amazon MQ `mq.t3.micro`, single-instance | ~$24 (or ~$8 if self-hosted on Fargate instead) |
| Fargate: 4 standard tasks (`api-core`, `metrics-engine`, `identity-service`, `ingestion-writer`) @ 0.25 vCPU/0.5 GB, 24/7 | ~$36 |
| Fargate Spot: 5 connector tasks @ 0.25 vCPU/0.5 GB, 24/7 | ~$14 |
| Application Load Balancer (base + light LCU usage) | ~$18 |
| S3 + CloudFront (frontend, low traffic) | ~$3 |
| ECR storage (10 small images) | ~$1 |
| Secrets Manager (~10 secrets) | ~$4 |
| CloudWatch Logs (ingestion + short retention) | ~$8 |
| Route 53 hosted zone + queries | ~$1 |
| NAT gateway (for the connectors' outbound calls to GitHub/GitLab/Jira/Jenkins) | ~$33 (this is the single line item worth optimizing — see note below) |
| **Total** | **~$155–165/month** |

**Cost-optimization note on the NAT gateway:** it's the single biggest line item here relative to
its purpose (letting private-subnet Fargate tasks reach the public internet to call vendor APIs).
Two ways to bring it down: (a) a single NAT gateway instead of one per AZ (acceptable at this
traffic level — the only risk is an AZ outage briefly affecting outbound connector calls, not
data loss, since everything is retry-safe), which is what the estimate above already assumes; or
(b) skip the NAT gateway entirely and place the connector tasks in a public subnet with a
security group that only allows outbound traffic (no inbound except from the ALB for the two that
receive webhooks) — cuts this line to near zero at the cost of a slightly less conventional
network layout. For a cost-constrained pilot, (b) is a reasonable, defensible choice.

## 5. Step-by-step deployment (Tier 2)

### 5.1 Prerequisites
Generate the per-integration credentials listed in the deployment guide's §7.1 before starting —
you'll enter them into Secrets Manager in step 5.6.

### 5.2 Network
1. Create a VPC (e.g. `10.0.0.0/16`) with 2 public subnets and 2 private subnets across 2 AZs.
2. One Internet Gateway (public subnets), one NAT Gateway in a public subnet (see the cost note
   above for the cheaper alternative).
3. Security groups: `alb-sg` (inbound 443 from `0.0.0.0/0`), `app-sg` (inbound from `alb-sg`
   only, on each service's port), `db-sg` (inbound 5432 from `app-sg` only), `mq-sg` (inbound
   5671/5672 from `app-sg` only). Nothing should be reachable directly from the internet except
   the ALB.

### 5.3 Database
1. Provision RDS PostgreSQL 16, `db.t4g.micro`, single-AZ, 20 GB gp3, in the private subnets, in
   `db-sg`. Enable automated backups (7-day retention is a sensible default for this workload).
2. Note the endpoint — this becomes `DB_URL` for every backend service.
3. **Do not** run any migration manually. `api-core`'s first successful boot against this
   database runs every Flyway migration and creates all three schemas.

### 5.4 Message queue
1. Provision Amazon MQ for RabbitMQ, single-instance `mq.t3.micro`, in the private subnets, in
   `mq-sg`. (Or: add a 10th Fargate task running the official `rabbitmq:3-management-alpine`
   image with an EFS-backed volume for `/var/lib/rabbitmq`, if self-hosting to save the cost
   delta noted in §4.)
2. Note the AMQP endpoint — this becomes `RABBITMQ_HOST` (plus port/username/password) for every
   connector, `ingestion-writer`, and `identity-service`.

### 5.5 Container images
1. Create one ECR repository per service (10 total: 9 backend + optionally the frontend if you
   choose to containerize it rather than use S3/CloudFront).
2. Build and push each backend service using the same multi-stage Dockerfile pattern
   `connector-gitlab` already uses (deployment guide §6) — build context is the Maven reactor
   root (`services/`), so each image's build step is identical apart from which module it
   packages.
3. Build the frontend (`npm run build`) and upload the `dist/` output to an S3 bucket configured
   for static website hosting, or route it through CloudFront directly from the bucket (the
   latter is the standard, recommended shape — it gets you TLS and caching essentially for
   free).

### 5.6 Secrets
Create one Secrets Manager secret per credential in the deployment guide's §7.3 table (DB
credentials, MQ credentials, `GITHUB_TOKEN`, `GITLAB_TOKEN`, `JIRA_EMAIL`/`JIRA_API_TOKEN`,
`JENKINS_USERNAME`/`JENKINS_API_TOKEN`). Reference these in each Fargate task definition's
`secrets` block (never as plain `environment` values) — the task execution role needs
`secretsmanager:GetSecretValue` scoped to exactly the secrets that service needs, nothing broader.

### 5.7 ECS cluster and services
1. Create an ECS cluster (Fargate launch type — no EC2 capacity to manage).
2. One task definition per backend service, referencing its ECR image, its env vars (plain
   config like ports/URLs) and secrets (from step 5.6), sized per §3's table.
3. One ECS service per task definition, desired count **1** for every service at this scale (no
   auto-scaling needed — see §7 for when to revisit this), capacity provider `FARGATE_SPOT` for
   the 5 connectors and `FARGATE` for the other 4.
4. `api-core` (and any connector receiving live webhooks — GitHub, GitLab, Jira) needs an ALB
   target group; the others (`metrics-engine`, `identity-service`, `ingestion-writer`, and
   `connector-jenkins`/`connector-ai-telemetry`, which have no inbound traffic beyond internal
   admin calls) don't need to be internet-reachable at all — only reachable from `api-core` on
   the private network for the handful that `api-core` calls directly (`connector-github`,
   `connector-gitlab`, `connector-jira`, `connector-jenkins`'s `/internal/backfill`).

### 5.8 Load balancer, DNS, TLS
1. Create the ALB in the public subnets, `alb-sg`.
2. Request an ACM certificate for your domain (e.g. `app.yourcompany.com` for the frontend,
   `api.yourcompany.com` for `api-core`, and one hostname per connector that needs a public
   webhook URL, e.g. `github-hooks.yourcompany.com`).
3. ALB listener rules: route each hostname to the matching target group.
4. Route 53: create the hosted zone (if not already existing) and an alias record per hostname
   pointing at the ALB (and a separate one for the CloudFront distribution serving the frontend).

### 5.9 Deployment order
Follow the deployment guide's §7.2 order exactly (Postgres → Amazon MQ → `api-core` →
`ingestion-writer`/`identity-service` → `metrics-engine` → connectors → frontend) — nothing here
is AWS-specific, it's the same dependency chain regardless of where it runs.

### 5.10 Verify
1. `api-core`'s ALB health check (`GET /actuator/health`) should go green first.
2. Connect one real repo through the Admin console and confirm its sync status reaches "Synced"
   within a few minutes.
3. Confirm a Cockpit tile shows a real, non-null number for that repo.

## 6. Tier 1 — single EC2 instance (cheaper alternative)

For a genuine pilot or a hard budget ceiling:

1. Launch one EC2 instance — `t4g.large` (2 vCPU, 8 GB RAM, Graviton) is comfortably enough for
   all 9 backend services + Postgres + RabbitMQ at this scale, on-demand cost roughly $50/month
   (or ~$30/month with a 1-year no-upfront reserved instance, if you're confident this will run
   long-term).
2. Install Docker + Docker Compose. Clone the repo, run `docker compose -f infra/docker-compose.yml
   up -d` for Postgres/RabbitMQ, then `infra/start-backend.sh` for the 8 plain-process services
   (they'll run as background processes on the same box; consider wrapping them in `systemd`
   unit files instead of relying on the script's own process management for anything meant to
   survive a reboot).
3. Put an Nginx (or Caddy, for automatic Let's Encrypt TLS) reverse proxy in front, routing each
   public hostname to its service's local port.
4. Attach an Elastic IP so the instance's address doesn't change on a stop/start, and point Route
   53 records at it.
5. Back up the Postgres data directory (an EBS snapshot on a schedule is the simplest approach)
   — there's no managed automated-backup story on this tier, so this step is not optional.

**The honest trade-off:** this is meaningfully less resilient than Tier 2 — a memory leak in one
connector, or an OS-level issue, can degrade or take down every service at once, and there's no
automated failover or patching. It's a legitimate choice for a time-boxed pilot; it's not what
you want to still be running a year in in with 20 developers depending on the dashboards daily.

## 7. When to move past this plan

Revisit sizing (not necessarily architecture) when any of these happen:
- More than ~20 connected repos with heavy CI activity — bump `metrics-engine` and
  `ingestion-writer` from Fargate minimum to `0.5 vCPU / 1 GB`.
- Dashboard latency becomes noticeable with more concurrent viewers — add a second `api-core`
  task behind the same ALB target group (it's stateless; this needs no code change).
- The organization's risk tolerance changes (this becomes a system people depend on daily with an
  expectation of uptime) — move RDS and Amazon MQ to Multi-AZ, and run 2 tasks per critical
  service across 2 AZs.
- Approaching genuine multi-tenancy (serving more than one organization) — this is explicitly
  **not** supported today (see the functional specification's "out of scope" section) and needs
  its own design pass, not just more infrastructure.
