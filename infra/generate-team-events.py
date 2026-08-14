#!/usr/bin/env python3
"""Generates several demo teams (team.snapshot events) plus ~30 days of per-team deploy/PR
history, so the Teams view shows more than the single team infra/smoke-e2e.sh creates.

Each team gets its own GitHub team id (source="github", sourceId=<id>), which
TeamImportService.upsertTeam upserts on — reusing an id here would rename/update that team
rather than create a new one, so these ids must not collide with smoke-e2e.sh's hardcoded 9001.
Team "quality" tunes deploy frequency and change-failure rate so different teams land in
different DORA tiers, deliberately echoing the same team names/relative performance used in the
frontend's Investment Profile mock data (Mobile/Payments strong, Platform weakest) for a
consistent demo narrative across the real Cockpit and the mock Investment Profile page.

Emits tab-separated lines to stdout: <github-event-type>\t<delivery-id>\t<json-body>
"""
import datetime
import json
import random
import sys

WINDOW_DAYS = 30

# quality: (avg deploys/day, failure probability per deploy)
TEAMS = [
    {"id": 9101, "name": "Payments", "repo": "ai-impact-evaluation/payments-service", "extra_repos": ["ai-impact-evaluation/payments-gateway"],
     "members": [{"id": 610101, "login": "a-rao"}, {"id": 610102, "login": "k-singh"}],
     "deploys_per_day": 1.5, "failure_rate": 0.05},
    {"id": 9102, "name": "Platform", "repo": "ai-impact-evaluation/platform-core",
     "extra_repos": ["ai-impact-evaluation/platform-infra", "ai-impact-evaluation/platform-auth"],
     "members": [{"id": 610201, "login": "m-chen"}, {"id": 610202, "login": "j-alvarez"}, {"id": 610203, "login": "p-nair"}],
     "deploys_per_day": 0.15, "failure_rate": 0.4},
    {"id": 9103, "name": "Growth", "repo": "ai-impact-evaluation/growth-web", "extra_repos": [],
     "members": [{"id": 610301, "login": "s-okafor"}],
     "deploys_per_day": 0.35, "failure_rate": 0.2},
    {"id": 9104, "name": "Mobile", "repo": "ai-impact-evaluation/mobile-ios", "extra_repos": ["ai-impact-evaluation/mobile-android"],
     "members": [{"id": 610401, "login": "l-fischer"}, {"id": 610402, "login": "d-park"}],
     "deploys_per_day": 1.3, "failure_rate": 0.05},
    {"id": 9105, "name": "Checkout", "repo": "ai-impact-evaluation/checkout-service", "extra_repos": [],
     "members": [{"id": 610501, "login": "r-kapoor"}],
     "deploys_per_day": 0.5, "failure_rate": 0.15},
]


def iso(dt: datetime.datetime) -> str:
    return dt.strftime("%Y-%m-%dT%H:%M:%SZ")


def emit(event_type: str, delivery: str, body: dict) -> None:
    sys.stdout.write(f"{event_type}\t{delivery}\t{json.dumps(body, separators=(',', ':'))}\n")


def main() -> None:
    random.seed(20260706)
    now = datetime.datetime.utcnow()
    wf_id = 950000
    pr_id = 850000

    for team in TEAMS:
        snapshot = {
            "id": team["id"],
            "name": team["name"],
            "slug": team["name"].lower(),
            "repositories": [{"full_name": team["repo"]}] + [{"full_name": r} for r in team["extra_repos"]],
            "members": team["members"],
        }
        emit("team.snapshot", f"seed-team-{team['id']}", snapshot)

    for team in TEAMS:
        repo = team["repo"]
        for offset in range(WINDOW_DAYS - 1, -1, -1):
            day = now - datetime.timedelta(days=offset)

            deploy_chance = team["deploys_per_day"]
            n_deploys = 1 if random.random() < deploy_chance else 0
            if deploy_chance > 1:
                n_deploys = 1 + (1 if random.random() < (deploy_chance - 1) else 0)
            for _ in range(n_deploys):
                wf_id += 1
                deploy_time = day.replace(hour=random.randint(8, 20), minute=random.randint(0, 59), second=0, microsecond=0)
                failed = random.random() < team["failure_rate"]
                emit("workflow_run", f"seed-team-wf-{wf_id}", {
                    "action": "completed",
                    "workflow_run": {
                        "id": wf_id, "name": "Deploy production",
                        "conclusion": "failure" if failed else "success",
                        "status": "completed", "updated_at": iso(deploy_time),
                    },
                    "repository": {"full_name": repo},
                })
                if failed:
                    wf_id += 1
                    hotfix_time = deploy_time + datetime.timedelta(hours=random.choice([1, 2, 4, 8]))
                    emit("workflow_run", f"seed-team-hotfix-{wf_id}", {
                        "action": "completed",
                        "workflow_run": {
                            "id": wf_id, "name": "Hotfix production", "conclusion": "success",
                            "status": "completed", "updated_at": iso(hotfix_time),
                        },
                        "repository": {"full_name": repo},
                    })

            if random.random() < 0.4:
                pr_id += 1
                lead_hours = random.choice([2, 4, 8, 16, 24, 36, 60, 96])
                merged_time = day.replace(hour=random.randint(9, 18), minute=random.randint(0, 59), second=0, microsecond=0)
                created_time = merged_time - datetime.timedelta(hours=lead_hours)
                emit("pull_request", f"seed-team-pr-{pr_id}", {
                    "action": "closed",
                    "pull_request": {
                        "id": pr_id, "created_at": iso(created_time), "merged_at": iso(merged_time),
                        "base": {"repo": {"full_name": repo}},
                    },
                })


if __name__ == "__main__":
    main()
