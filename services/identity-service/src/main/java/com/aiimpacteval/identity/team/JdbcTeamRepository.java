package com.aiimpacteval.identity.team;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JdbcTeamRepository implements TeamRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTeamRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UUID upsertTeam(String source, String sourceId, String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO core.team (name, source, source_id)
                VALUES (?, ?, ?)
                ON CONFLICT ON CONSTRAINT uq_team_source DO UPDATE SET name = EXCLUDED.name
                RETURNING id
                """, UUID.class, name, source, sourceId);
    }

    @Override
    public void mapRepo(UUID teamId, String repo) {
        jdbcTemplate.update("""
                INSERT INTO core.team_repo (team_id, repo) VALUES (?, ?)
                ON CONFLICT (team_id, repo) DO NOTHING
                """, teamId, repo);
    }

    @Override
    public void addMember(UUID teamId, UUID contributorId) {
        jdbcTemplate.update("""
                INSERT INTO core.team_member (team_id, contributor_id) VALUES (?, ?)
                ON CONFLICT (team_id, contributor_id) DO NOTHING
                """, teamId, contributorId);
    }
}
