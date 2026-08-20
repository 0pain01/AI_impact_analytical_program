package com.aiimpacteval.apicore.admin;

import com.aiimpacteval.apicore.admin.ConnectorAdminService.RepoSyncStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Lets an ADMIN connect a new GitHub repo (optionally assigning it to a team in the same step),
 * import an org's teams, or check live per-repo sync status from the Admin console (PRD
 * E1-S4/E8), instead of calling connector-github's internal backfill endpoints from a terminal.
 * ADMIN-only — covered by the existing {@code /api/v1/admin/**} rule in SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/admin/connectors")
public class ConnectorAdminController {

    private final ConnectorAdminService connectorAdminService;

    public ConnectorAdminController(ConnectorAdminService connectorAdminService) {
        this.connectorAdminService = connectorAdminService;
    }

    public record ConnectRepoRequest(String owner, String repo, UUID teamId) {
    }

    public record TriggeredResponse(boolean triggered) {
    }

    @PostMapping("/repos")
    public ResponseEntity<TriggeredResponse> connectRepo(@RequestBody ConnectRepoRequest request, Authentication auth,
                                                          HttpServletRequest servletRequest) {
        if (isBlank(request.owner()) || isBlank(request.repo())) {
            return ResponseEntity.badRequest().build();
        }
        connectorAdminService.connectRepo(auth.getName(), request.owner().trim(), request.repo().trim(),
                request.teamId(), servletRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new TriggeredResponse(true));
    }

    @GetMapping("/repos")
    public List<RepoSyncStatus> listRepoSyncStatus() {
        return connectorAdminService.listRepoSyncStatus();
    }

    // repo is a query param, not a path segment — "owner/repo" contains a slash a single path
    // variable can't hold without hitting Tomcat's encoded-slash rejection (same reason
    // TeamAdminController's remove-repo endpoint takes it this way).
    @DeleteMapping("/repos")
    public ResponseEntity<Void> disconnectRepo(@RequestParam String repo, Authentication auth,
                                               HttpServletRequest servletRequest) {
        if (isBlank(repo)) {
            return ResponseEntity.badRequest().build();
        }
        connectorAdminService.disconnectRepo(auth.getName(), repo.trim(), servletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    public record ConnectGithubTeamsRequest(String org) {
    }

    @PostMapping("/github-teams")
    public ResponseEntity<TriggeredResponse> connectGithubTeams(@RequestBody ConnectGithubTeamsRequest request,
                                                                 Authentication auth, HttpServletRequest servletRequest) {
        if (isBlank(request.org())) {
            return ResponseEntity.badRequest().build();
        }
        connectorAdminService.connectGithubOrgTeams(auth.getName(), request.org().trim(), servletRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new TriggeredResponse(true));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
