package com.aiimpacteval.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.identity.resolve.IdentityResolver;
import com.aiimpacteval.identity.resolve.ObservedIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityEventListenerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    // extract() never touches the resolver or team importer; null keeps this test pure.
    private final IdentityEventListener listener = new IdentityEventListener(new IdentityResolver(null), null);

    private EventEnvelope envelope(String source, String eventType, String json) throws Exception {
        return new EventEnvelope(source, "sid", eventType, Instant.now(), "0.1.0", MAPPER.readTree(json));
    }

    @Test
    void extractsPrAuthorFromWebhookAndSnapshot() throws Exception {
        String webhook = "{\"pull_request\":{\"user\":{\"id\":42,\"login\":\"v-sharma\"}}}";
        String snapshot = "{\"user\":{\"id\":42,\"login\":\"v-sharma\"}}";

        List<ObservedIdentity> fromWebhook = listener.extract(envelope("github", "pull_request", webhook));
        List<ObservedIdentity> fromSnapshot = listener.extract(envelope("github", "pull_request.snapshot", snapshot));

        assertEquals(List.of(new ObservedIdentity("github", "42", "v-sharma", null)), fromWebhook);
        assertEquals(fromWebhook, fromSnapshot);
    }

    @Test
    void extractsCommitAuthorPreferringPlatformId() throws Exception {
        String commit = """
                {"sha":"abc","author":{"id":42,"login":"v-sharma"},
                 "commit":{"author":{"name":"Vishal Sharma","email":"v@example.com"}}}""";

        var found = listener.extract(envelope("github", "commit.snapshot", commit));

        assertEquals(List.of(new ObservedIdentity("github", "42", "Vishal Sharma", "v@example.com")), found);
    }

    @Test
    void fallsBackToEmailHandleWhenNoPlatformAccount() throws Exception {
        String commit = """
                {"sha":"abc","author":null,
                 "commit":{"author":{"name":"Ext Contributor","email":"Ext@Example.com"}}}""";

        var found = listener.extract(envelope("github", "commit.snapshot", commit));

        assertEquals(1, found.size());
        assertEquals("email:ext@example.com", found.get(0).sourceUserId());
    }

    @Test
    void extractsJiraAssigneeReporterAndActor() throws Exception {
        String issue = """
                {"webhookEvent":"jira:issue_updated",
                 "user":{"accountId":"act-1","displayName":"Actor"},
                 "issue":{"fields":{
                   "assignee":{"accountId":"acc-2","displayName":"Assignee","emailAddress":"a@example.com"},
                   "reporter":{"accountId":"acc-3","displayName":"Reporter"}}}}""";

        var found = listener.extract(envelope("jira", "jira:issue_updated", issue));

        assertEquals(3, found.size());
        assertTrue(found.contains(new ObservedIdentity("jira", "acc-2", "Assignee", "a@example.com")));
        assertTrue(found.contains(new ObservedIdentity("jira", "act-1", "Actor", null)));
    }

    @Test
    void oddPayloadsExtractNothingWithoutThrowing() throws Exception {
        assertTrue(listener.extract(envelope("github", "pull_request", "{}")).isEmpty());
        assertTrue(listener.extract(envelope("jira", "jira:issue_updated", "{\"issue\":{}}")).isEmpty());
        assertTrue(listener.extract(envelope("sonarqube", "scan", "{}")).isEmpty());
    }
}
