package com.aiimpacteval.identity.resolve;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityResolverTest {

    static class InMemoryRepository implements IdentityRepository {
        record Contributor(UUID id, String name, String email, boolean isBot) {
        }

        record Alias(UUID contributorId, String source, String sourceUserId, BigDecimal confidence) {
        }

        final Map<UUID, Contributor> contributors = new HashMap<>();
        final List<Alias> aliases = new ArrayList<>();
        final Map<String, String> aliasEmails = new HashMap<>();

        @Override
        public Optional<UUID> findContributorByAlias(String source, String sourceUserId) {
            return aliases.stream()
                    .filter(a -> a.source().equals(source) && a.sourceUserId().equals(sourceUserId))
                    .map(Alias::contributorId).findFirst();
        }

        @Override
        public Optional<UUID> findContributorByEmail(String email) {
            return contributors.values().stream()
                    .filter(c -> email.equalsIgnoreCase(String.valueOf(c.email())))
                    .map(Contributor::id).findFirst()
                    .or(() -> aliases.stream()
                            .filter(a -> email.equalsIgnoreCase(aliasEmails.get(a.source() + "/" + a.sourceUserId())))
                            .map(Alias::contributorId).findFirst());
        }

        @Override
        public UUID insertContributor(String name, String email, boolean isBot) {
            UUID id = UUID.randomUUID();
            contributors.put(id, new Contributor(id, name, email, isBot));
            return id;
        }

        @Override
        public void insertAlias(UUID contributorId, String source, String sourceUserId,
                                String name, String email, BigDecimal confidence) {
            aliases.add(new Alias(contributorId, source, sourceUserId, confidence));
            if (email != null) {
                aliasEmails.put(source + "/" + sourceUserId, email);
            }
        }
    }

    private final InMemoryRepository repo = new InMemoryRepository();
    private final IdentityResolver resolver = new IdentityResolver(repo);

    @Test
    void sameAliasResolvesToSameContributorWithoutDuplicates() {
        var observed = new ObservedIdentity("github", "12345", "v-sharma", null);
        UUID first = resolver.resolve(observed);
        UUID second = resolver.resolve(observed);

        assertEquals(first, second);
        assertEquals(1, repo.contributors.size());
        assertEquals(1, repo.aliases.size());
    }

    @Test
    void crossToolIdentitiesMergeOnEmail() {
        UUID github = resolver.resolve(new ObservedIdentity("github", "12345", "v-sharma", "Vishal@Example.com"));
        UUID jira = resolver.resolve(new ObservedIdentity("jira", "acc-99", "Vishal S", "vishal@example.com"));

        assertEquals(github, jira, "same normalized email must merge into one contributor");
        assertEquals(1, repo.contributors.size());
        assertEquals(2, repo.aliases.size());
        assertEquals(IdentityResolver.EMAIL_MATCH_CONFIDENCE, repo.aliases.get(1).confidence(),
                "heuristic merge must carry reviewable confidence");
    }

    @Test
    void differentEmailsStaySeparate() {
        UUID a = resolver.resolve(new ObservedIdentity("github", "1", "A", "a@example.com"));
        UUID b = resolver.resolve(new ObservedIdentity("jira", "2", "A", "a@other.com"));

        assertNotEquals(a, b, "no email match → never silently merged (E2-S1 acceptance)");
        assertEquals(2, repo.contributors.size());
    }

    @Test
    void noEmailCreatesUnmergedContributor() {
        UUID a = resolver.resolve(new ObservedIdentity("github", "1", "Someone", null));
        UUID b = resolver.resolve(new ObservedIdentity("jira", "acc-1", "Someone", null));

        assertNotEquals(a, b);
    }

    @Test
    void botsAreDetected() {
        assertTrue(new ObservedIdentity("github", "9", "dependabot[bot]", null).isBot());
        assertTrue(new ObservedIdentity("github", "10", "renovate-bot", null).isBot());
        assertFalse(new ObservedIdentity("github", "11", "vishal", "v@example.com").isBot());

        resolver.resolve(new ObservedIdentity("github", "9", "dependabot[bot]", null));
        assertTrue(repo.contributors.values().iterator().next().isBot());
    }
}
