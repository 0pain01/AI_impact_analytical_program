package com.aiimpacteval.identity.team;

import com.aiimpacteval.identity.resolve.IdentityResolver;
import com.aiimpacteval.identity.resolve.ObservedIdentity;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Imports team structure from a connected source (E2-S2, FR-1.5): upserts the team, maps its
 * repositories (feeding metrics-engine team rollups), and resolves each member through the
 * existing identity resolver so team rosters share the same contributor identities as
 * everything else.
 *
 * <p>{@code source} tags both the team row (so a GitHub org sync and a GitLab group import
 * never collide on the same team, mirroring "manual" teams already coexisting with "github"
 * ones) and each member's {@link ObservedIdentity} (GitLab user IDs are a distinct identity
 * space from GitHub's — the resolver merges across sources by email, same as it already does
 * for GitHub/Jira).
 */
@Service
public class TeamImportService {

    private final TeamRepository teamRepository;
    private final IdentityResolver identityResolver;

    public TeamImportService(TeamRepository teamRepository, IdentityResolver identityResolver) {
        this.teamRepository = teamRepository;
        this.identityResolver = identityResolver;
    }

    public void importSnapshot(String source, TeamSnapshot snapshot) {
        UUID teamId = teamRepository.upsertTeam(source, snapshot.teamId(), snapshot.name());

        for (String repo : snapshot.repoFullNames()) {
            teamRepository.mapRepo(teamId, repo);
        }

        for (TeamSnapshot.MemberRef member : snapshot.members()) {
            UUID contributorId = identityResolver.resolve(
                    new ObservedIdentity(source, member.sourceUserId(), member.login(), null));
            teamRepository.addMember(teamId, contributorId);
        }
    }
}
