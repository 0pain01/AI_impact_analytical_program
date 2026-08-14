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
 */
@Service
public class TeamImportService {

    private final TeamRepository teamRepository;
    private final IdentityResolver identityResolver;

    public TeamImportService(TeamRepository teamRepository, IdentityResolver identityResolver) {
        this.teamRepository = teamRepository;
        this.identityResolver = identityResolver;
    }

    public void importSnapshot(TeamSnapshot snapshot) {
        UUID teamId = teamRepository.upsertTeam("github", snapshot.teamId(), snapshot.name());

        for (String repo : snapshot.repoFullNames()) {
            teamRepository.mapRepo(teamId, repo);
        }

        for (TeamSnapshot.MemberRef member : snapshot.members()) {
            UUID contributorId = identityResolver.resolve(
                    new ObservedIdentity("github", member.sourceUserId(), member.login(), null));
            teamRepository.addMember(teamId, contributorId);
        }
    }
}
