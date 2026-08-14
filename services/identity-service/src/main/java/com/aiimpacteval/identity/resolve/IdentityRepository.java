package com.aiimpacteval.identity.resolve;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for identity resolution; JDBC in production, in-memory fake in tests. */
public interface IdentityRepository {

    Optional<UUID> findContributorByAlias(String source, String sourceUserId);

    Optional<UUID> findContributorByEmail(String normalizedEmail);

    UUID insertContributor(String canonicalName, String canonicalEmail, boolean isBot);

    void insertAlias(UUID contributorId, String source, String sourceUserId,
                     String name, String email, BigDecimal matchConfidence);
}
