package com.aiimpacteval.identity.team;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the connector-gitlab {@code group.snapshot} payload shape into a {@link TeamSnapshot} —
 * GitLab's group is the closest analogue to a GitHub org team (see GitlabGroupBackfillService's
 * javadoc). Repo full names come out already prefixed {@code "gitlab:"}, matching the same
 * prefix {@code StagingEventWriter} stamps onto GitLab merge-request/pipeline rows — required
 * for {@code core.team_repo} to actually join against those tables (see its class javadoc).
 */
public final class GitlabGroupSnapshotParser {

    private GitlabGroupSnapshotParser() {
    }

    public static TeamSnapshot parse(JsonNode payload) {
        List<String> repos = new ArrayList<>();
        payload.path("projects").forEach(p -> {
            if (p.hasNonNull("path_with_namespace")) {
                repos.add("gitlab:" + p.get("path_with_namespace").asText());
            }
        });

        List<TeamSnapshot.MemberRef> members = new ArrayList<>();
        payload.path("members").forEach(m -> {
            if (m.hasNonNull("id")) {
                members.add(new TeamSnapshot.MemberRef(m.get("id").asText(),
                        m.hasNonNull("username") ? m.get("username").asText() : null));
            }
        });

        return new TeamSnapshot(
                payload.get("id").asText(),
                payload.get("name").asText(),
                payload.hasNonNull("path") ? payload.get("path").asText() : null,
                repos, members);
    }
}
