package com.aiimpacteval.identity.team;

import com.aiimpacteval.identity.resolve.IdentityRepository;
import com.aiimpacteval.identity.resolve.IdentityResolver;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamImportServiceTest {

    /** Every alias becomes its own contributor — sufficient for exercising team wiring. */
    static class AlwaysNewContributorRepository implements IdentityRepository {
        @Override
        public Optional<UUID> findContributorByAlias(String source, String sourceUserId) {
            return Optional.empty();
        }

        @Override
        public Optional<UUID> findContributorByEmail(String normalizedEmail) {
            return Optional.empty();
        }

        @Override
        public UUID insertContributor(String canonicalName, String canonicalEmail, boolean isBot) {
            return UUID.randomUUID();
        }

        @Override
        public void insertAlias(UUID contributorId, String source, String sourceUserId,
                                String name, String email, BigDecimal matchConfidence) {
            // not asserted in this test
        }
    }

    static class InMemoryTeamRepository implements TeamRepository {
        final Map<String, UUID> teamsBySourceKey = new HashMap<>();
        final List<String> teamNames = new ArrayList<>();
        final List<Object[]> repoMappings = new ArrayList<>(); // [teamId, repo]
        final Set<List<Object>> memberMappings = new HashSet<>(); // {teamId, contributorId}

        @Override
        public UUID upsertTeam(String source, String sourceId, String name) {
            String key = source + "/" + sourceId;
            teamNames.add(name);
            return teamsBySourceKey.computeIfAbsent(key, k -> UUID.randomUUID());
        }

        @Override
        public void mapRepo(UUID teamId, String repo) {
            repoMappings.add(new Object[] {teamId, repo});
        }

        @Override
        public void addMember(UUID teamId, UUID contributorId) {
            memberMappings.add(List.of(teamId, contributorId));
        }
    }

    private final InMemoryTeamRepository teamRepository = new InMemoryTeamRepository();
    private final IdentityResolver identityResolver = new IdentityResolver(new AlwaysNewContributorRepository());
    private final TeamImportService service = new TeamImportService(teamRepository, identityResolver);

    @Test
    void importsTeamRepoAndMemberMappings() {
        var snapshot = new TeamSnapshot("555", "Platform Team", "platform-team",
                List.of("acme/app", "acme/lib"),
                List.of(new TeamSnapshot.MemberRef("42", "v-sharma"),
                        new TeamSnapshot.MemberRef("43", "a-iyer")));

        service.importSnapshot(snapshot);

        assertEquals(1, teamRepository.teamsBySourceKey.size());
        assertEquals(2, teamRepository.repoMappings.size());
        assertEquals(2, teamRepository.memberMappings.size());
    }

    @Test
    void reimportingSameTeamUpsertsRatherThanDuplicating() {
        var snapshot = new TeamSnapshot("555", "Platform Team", "platform-team",
                List.of("acme/app"), List.of());

        service.importSnapshot(snapshot);
        service.importSnapshot(snapshot);

        assertEquals(1, teamRepository.teamsBySourceKey.size(), "same source team must resolve to one team id");
        assertTrue(teamRepository.teamNames.stream().allMatch("Platform Team"::equals));
    }

    @Test
    void emptyTeamImportsWithNoMappings() {
        var snapshot = new TeamSnapshot("1", "Empty Team", "empty", List.of(), List.of());

        service.importSnapshot(snapshot);

        assertEquals(1, teamRepository.teamsBySourceKey.size());
        assertEquals(0, teamRepository.repoMappings.size());
        assertEquals(0, teamRepository.memberMappings.size());
    }
}
