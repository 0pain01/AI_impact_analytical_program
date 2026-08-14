package com.aiimpacteval.apicore.admin;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Manual team/org-structure administration (complements the read-only {@code TeamController}).
 * ADMIN-only — covered by the existing {@code /api/v1/admin/**} rule in SecurityConfig, no
 * separate wiring needed. See {@link TeamAdminService} for why this is source-tagged
 * {@code 'manual'} rather than writing raw SQL by hand each time.
 */
@RestController
@RequestMapping("/api/v1/admin/teams")
public class TeamAdminController {

    private final TeamAdminService adminService;

    public TeamAdminController(TeamAdminService adminService) {
        this.adminService = adminService;
    }

    public record CreateTeamRequest(String name, UUID parentTeamId) {
    }

    public record TeamCreated(UUID id) {
    }

    @PostMapping
    public TeamCreated createOrUpdateTeam(@RequestBody CreateTeamRequest request) {
        UUID id = adminService.upsertTeam(request.name(), request.parentTeamId());
        return new TeamCreated(id);
    }

    public record RepoRequest(String repo) {
    }

    @PostMapping("/{teamId}/repos")
    public List<String> addRepo(@PathVariable UUID teamId, @RequestBody RepoRequest request) {
        adminService.addRepo(teamId, request.repo());
        return adminService.listRepos(teamId);
    }

    @DeleteMapping("/{teamId}/repos/{repo}")
    public List<String> removeRepo(@PathVariable UUID teamId, @PathVariable String repo) {
        adminService.removeRepo(teamId, repo);
        return adminService.listRepos(teamId);
    }

    @GetMapping("/{teamId}/repos")
    public List<String> listRepos(@PathVariable UUID teamId) {
        return adminService.listRepos(teamId);
    }
}