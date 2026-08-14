package com.aiimpacteval.identity.team;

import java.util.UUID;

/** Persistence port for team import (E2-S2); JDBC in production, in-memory fake in tests. */
public interface TeamRepository {

    /** Upserts by (source, sourceId) — matches {@code core.team}'s {@code uq_team_source}. */
    UUID upsertTeam(String source, String sourceId, String name);

    void mapRepo(UUID teamId, String repo);

    void addMember(UUID teamId, UUID contributorId);
}
