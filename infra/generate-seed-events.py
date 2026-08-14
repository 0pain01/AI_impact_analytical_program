#!/usr/bin/env python3
"""Generates ~30 days of synthetic GitHub Actions/PR events for Cockpit demo seeding.

Used by infra/seed-demo-history.sh to give Cockpit's real DORA charts (radar, trend, tier
bands) enough day-over-day variation to be worth looking at — the E2E smoke test
(infra/smoke-e2e.sh) intentionally posts only one event per metric, which is correct for a
pipeline test but leaves every Cockpit trend chart at a single data point.

Emits tab-separated lines to stdout: <github-event-type>\t<delivery-id>\t<json-body>
The caller is responsible for HMAC-signing and POSTing each line to connector-github.
"""
import datetime
import json
import random
import sys

REPO = "ai-impact-evaluation/demo"
WINDOW_DAYS = 30
SEED = 20260706  # fixed seed: reproducible output across runs, not true randomness


def iso(dt: datetime.datetime) -> str:
    return dt.strftime("%Y-%m-%dT%H:%M:%SZ")


def main() -> None:
    random.seed(SEED)
    now = datetime.datetime.utcnow()
    wf_id = 900000
    pr_id = 800000
    events = []

    for offset in range(WINDOW_DAYS - 1, -1, -1):
        day = now - datetime.timedelta(days=offset)

        n_deploys = random.choices([0, 1, 2], weights=[1, 5, 3])[0]
        for _ in range(n_deploys):
            wf_id += 1
            deploy_time = day.replace(hour=random.randint(8, 20), minute=random.randint(0, 59), second=0, microsecond=0)
            failed = random.random() < 0.12
            events.append((
                "workflow_run",
                f"seed-wf-{wf_id}",
                {
                    "action": "completed",
                    "workflow_run": {
                        "id": wf_id,
                        "name": "Deploy production",
                        "conclusion": "failure" if failed else "success",
                        "status": "completed",
                        "updated_at": iso(deploy_time),
                    },
                    "repository": {"full_name": REPO},
                },
            ))
            if failed:
                wf_id += 1
                hotfix_time = deploy_time + datetime.timedelta(hours=random.choice([1, 2, 4, 8]))
                events.append((
                    "workflow_run",
                    f"seed-hotfix-{wf_id}",
                    {
                        "action": "completed",
                        "workflow_run": {
                            "id": wf_id,
                            "name": "Hotfix production",
                            "conclusion": "success",
                            "status": "completed",
                            "updated_at": iso(hotfix_time),
                        },
                        "repository": {"full_name": REPO},
                    },
                ))

        n_prs = random.choices([0, 1, 2], weights=[2, 5, 2])[0]
        for _ in range(n_prs):
            pr_id += 1
            lead_hours = random.choice([2, 4, 8, 16, 24, 36, 60, 96])
            merged_time = day.replace(hour=random.randint(9, 18), minute=random.randint(0, 59), second=0, microsecond=0)
            created_time = merged_time - datetime.timedelta(hours=lead_hours)
            events.append((
                "pull_request",
                f"seed-pr-{pr_id}",
                {
                    "action": "closed",
                    "pull_request": {
                        "id": pr_id,
                        "created_at": iso(created_time),
                        "merged_at": iso(merged_time),
                        "base": {"repo": {"full_name": REPO}},
                    },
                },
            ))

    for event_type, delivery_id, body in events:
        sys.stdout.write(f"{event_type}\t{delivery_id}\t{json.dumps(body, separators=(',', ':'))}\n")


if __name__ == "__main__":
    main()
