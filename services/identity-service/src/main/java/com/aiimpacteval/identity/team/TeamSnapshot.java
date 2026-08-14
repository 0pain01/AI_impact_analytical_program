package com.aiimpacteval.identity.team;

import java.util.List;

/** Parsed shape of a {@code github.team.snapshot} event payload (E2-S2). */
public record TeamSnapshot(String teamId, String name, String slug,
                           List<String> repoFullNames, List<MemberRef> members) {

    /** A team member as identified by the source tool — mirrors ObservedIdentity's inputs. */
    public record MemberRef(String sourceUserId, String login) {
    }
}
