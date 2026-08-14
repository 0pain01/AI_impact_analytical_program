package com.aiimpacteval.identity.team;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Parses the connector-github {@code team.snapshot} payload shape into a {@link TeamSnapshot}. */
public final class TeamSnapshotParser {

    private TeamSnapshotParser() {
    }

    public static TeamSnapshot parse(JsonNode payload) {
        List<String> repos = new ArrayList<>();
        payload.path("repositories").forEach(r -> {
            if (r.hasNonNull("full_name")) {
                repos.add(r.get("full_name").asText());
            }
        });

        List<TeamSnapshot.MemberRef> members = new ArrayList<>();
        payload.path("members").forEach(m -> {
            if (m.hasNonNull("id")) {
                members.add(new TeamSnapshot.MemberRef(m.get("id").asText(),
                        m.hasNonNull("login") ? m.get("login").asText() : null));
            }
        });

        return new TeamSnapshot(
                payload.get("id").asText(),
                payload.get("name").asText(),
                payload.hasNonNull("slug") ? payload.get("slug").asText() : null,
                repos, members);
    }
}
