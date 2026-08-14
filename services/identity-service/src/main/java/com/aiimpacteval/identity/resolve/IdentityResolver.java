package com.aiimpacteval.identity.resolve;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reconciles per-tool identities into canonical contributors (E2-S1, FR-1.4).
 *
 * <p>Heuristic v1: an alias seen before short-circuits; otherwise an exact normalized-email
 * match attaches to the existing contributor (confidence 0.90 — heuristic, Admin-reviewable);
 * otherwise a new contributor is created (confidence 1.00 — it IS its own identity).
 * Name-similarity matching and the Admin merge/split flow are the next E2 increments —
 * unresolved identities are never dropped or double-merged silently.
 */
@Service
public class IdentityResolver {

    static final BigDecimal EMAIL_MATCH_CONFIDENCE = new BigDecimal("0.90");
    static final BigDecimal NEW_CONTRIBUTOR_CONFIDENCE = new BigDecimal("1.00");
    private static final Logger log = LoggerFactory.getLogger(IdentityResolver.class);

    private final IdentityRepository repository;

    public IdentityResolver(IdentityRepository repository) {
        this.repository = repository;
    }

    /** @return the canonical contributor id this observation resolved to. */
    public UUID resolve(ObservedIdentity observed) {
        var existing = repository.findContributorByAlias(observed.source(), observed.sourceUserId());
        if (existing.isPresent()) {
            return existing.get();
        }

        String email = observed.normalizedEmail();
        if (email != null) {
            var byEmail = repository.findContributorByEmail(email);
            if (byEmail.isPresent()) {
                repository.insertAlias(byEmail.get(), observed.source(), observed.sourceUserId(),
                        observed.name(), email, EMAIL_MATCH_CONFIDENCE);
                log.debug("Attached {}:{} to contributor {} via email match",
                        observed.source(), observed.sourceUserId(), byEmail.get());
                return byEmail.get();
            }
        }

        UUID created = repository.insertContributor(
                observed.name() == null ? observed.sourceUserId() : observed.name(),
                email, observed.isBot());
        repository.insertAlias(created, observed.source(), observed.sourceUserId(),
                observed.name(), email, NEW_CONTRIBUTOR_CONFIDENCE);
        return created;
    }
}
