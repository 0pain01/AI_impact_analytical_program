package com.aiimpacteval.identity.resolve;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcIdentityRepository implements IdentityRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcIdentityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UUID> findContributorByAlias(String source, String sourceUserId) {
        return jdbcTemplate.queryForList(
                        "SELECT contributor_id FROM core.contributor_alias WHERE source = ? AND source_user_id = ?",
                        UUID.class, source, sourceUserId)
                .stream().findFirst();
    }

    @Override
    public Optional<UUID> findContributorByEmail(String normalizedEmail) {
        return jdbcTemplate.queryForList("""
                        SELECT c.id FROM core.contributor c
                        WHERE lower(c.canonical_email) = ?
                        UNION
                        SELECT a.contributor_id FROM core.contributor_alias a
                        WHERE lower(a.email) = ?
                        LIMIT 1
                        """, UUID.class, normalizedEmail, normalizedEmail)
                .stream().findFirst();
    }

    @Override
    public UUID insertContributor(String canonicalName, String canonicalEmail, boolean isBot) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO core.contributor (canonical_name, canonical_email, is_bot)
                VALUES (?, ?, ?) RETURNING id
                """, UUID.class, canonicalName, canonicalEmail, isBot);
    }

    @Override
    public void insertAlias(UUID contributorId, String source, String sourceUserId,
                            String name, String email, BigDecimal matchConfidence) {
        // Concurrent consumers may race on the same alias — the unique constraint wins, and
        // losing is fine: the alias exists.
        jdbcTemplate.update("""
                INSERT INTO core.contributor_alias (contributor_id, source, source_user_id, name, email, match_confidence)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT ON CONSTRAINT uq_alias_per_source DO NOTHING
                """, contributorId, source, sourceUserId, name, email, matchConfidence);
    }
}
