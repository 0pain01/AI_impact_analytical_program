package com.aiimpacteval.identity.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamSnapshotParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void parsesFullSnapshot() throws Exception {
        String json = """
                {"id":555,"name":"Platform Team","slug":"platform-team",
                 "repositories":[{"full_name":"acme/app"},{"full_name":"acme/lib"}],
                 "members":[{"id":42,"login":"v-sharma"},{"id":43,"login":"a-iyer"}]}""";

        TeamSnapshot snapshot = TeamSnapshotParser.parse(MAPPER.readTree(json));

        assertEquals("555", snapshot.teamId());
        assertEquals("Platform Team", snapshot.name());
        assertEquals("platform-team", snapshot.slug());
        assertEquals(2, snapshot.repoFullNames().size());
        assertTrue(snapshot.repoFullNames().contains("acme/app"));
        assertEquals(2, snapshot.members().size());
        assertEquals(new TeamSnapshot.MemberRef("42", "v-sharma"), snapshot.members().get(0));
    }

    @Test
    void toleratesMissingReposAndMembers() throws Exception {
        String json = """
                {"id":1,"name":"Empty Team"}""";

        TeamSnapshot snapshot = TeamSnapshotParser.parse(MAPPER.readTree(json));

        assertTrue(snapshot.repoFullNames().isEmpty());
        assertTrue(snapshot.members().isEmpty());
    }

    @Test
    void skipsRepoEntriesMissingFullName() throws Exception {
        String json = """
                {"id":1,"name":"T","repositories":[{"full_name":"acme/app"},{}]}""";

        TeamSnapshot snapshot = TeamSnapshotParser.parse(MAPPER.readTree(json));

        assertEquals(1, snapshot.repoFullNames().size());
    }
}
