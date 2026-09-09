package com.aiimpacteval.identity.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitlabGroupSnapshotParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void parsesFullSnapshotWithPrefixedRepoNames() throws Exception {
        String json = """
                {"id":555,"name":"Platform Team","path":"platform-team",
                 "projects":[{"path_with_namespace":"acme/app"},{"path_with_namespace":"acme/lib"}],
                 "members":[{"id":42,"username":"v-sharma"},{"id":43,"username":"a-iyer"}]}""";

        TeamSnapshot snapshot = GitlabGroupSnapshotParser.parse(MAPPER.readTree(json));

        assertEquals("555", snapshot.teamId());
        assertEquals("Platform Team", snapshot.name());
        assertEquals("platform-team", snapshot.slug());
        assertEquals(2, snapshot.repoFullNames().size());
        // Prefixed so a same-named GitLab project can never collide with a GitHub repo under
        // the shared pull_request_state/workflow_run_state schema — see StagingEventWriter.
        assertTrue(snapshot.repoFullNames().contains("gitlab:acme/app"));
        assertEquals(2, snapshot.members().size());
        assertEquals(new TeamSnapshot.MemberRef("42", "v-sharma"), snapshot.members().get(0));
    }

    @Test
    void toleratesMissingProjectsAndMembers() throws Exception {
        String json = """
                {"id":1,"name":"Empty Group"}""";

        TeamSnapshot snapshot = GitlabGroupSnapshotParser.parse(MAPPER.readTree(json));

        assertTrue(snapshot.repoFullNames().isEmpty());
        assertTrue(snapshot.members().isEmpty());
    }

    @Test
    void skipsProjectEntriesMissingPathWithNamespace() throws Exception {
        String json = """
                {"id":1,"name":"T","projects":[{"path_with_namespace":"acme/app"},{}]}""";

        TeamSnapshot snapshot = GitlabGroupSnapshotParser.parse(MAPPER.readTree(json));

        assertEquals(1, snapshot.repoFullNames().size());
    }
}
