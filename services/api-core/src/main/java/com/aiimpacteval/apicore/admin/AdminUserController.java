package com.aiimpacteval.apicore.admin;

import com.aiimpacteval.apicore.admin.AdminUserService.AppUserView;
import com.aiimpacteval.apicore.admin.AdminUserService.NoSuchAdminUserException;
import com.aiimpacteval.apicore.security.Role;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin console's user/role management panel (PRD E8-S1). ADMIN-only — covered by the existing
 * {@code /api/v1/admin/**} rule in SecurityConfig, same as {@link TeamAdminController}.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public List<AppUserView> listUsers() {
        return adminUserService.listUsers();
    }

    public record CreateUserRequest(String email, String displayName, String role, UUID teamId, String githubLogin) {
    }

    @PostMapping
    public AppUserView createUser(@RequestBody CreateUserRequest request, Authentication auth,
                                  HttpServletRequest servletRequest) {
        return adminUserService.createUser(auth.getName(), request.email(), request.displayName(),
                Role.fromString(request.role()), request.teamId(), request.githubLogin(),
                servletRequest.getRemoteAddr());
    }

    public record UpdateRoleRequest(String role, UUID teamId) {
    }

    @PatchMapping("/{userId}/role")
    public AppUserView updateRole(@PathVariable UUID userId, @RequestBody UpdateRoleRequest request,
                                  Authentication auth, HttpServletRequest servletRequest) {
        return adminUserService.updateRoleAndTeam(auth.getName(), userId, Role.fromString(request.role()),
                request.teamId(), servletRequest.getRemoteAddr());
    }

    public record UpdateGithubLoginRequest(String githubLogin) {
    }

    @PatchMapping("/{userId}/github-login")
    public AppUserView updateGithubLogin(@PathVariable UUID userId, @RequestBody UpdateGithubLoginRequest request,
                                         Authentication auth, HttpServletRequest servletRequest) {
        return adminUserService.updateGithubLogin(auth.getName(), userId, request.githubLogin(),
                servletRequest.getRemoteAddr());
    }

    public record SetActiveRequest(boolean active) {
    }

    @PatchMapping("/{userId}/active")
    public AppUserView setActive(@PathVariable UUID userId, @RequestBody SetActiveRequest request,
                                 Authentication auth, HttpServletRequest servletRequest) {
        return adminUserService.setActive(auth.getName(), userId, request.active(), servletRequest.getRemoteAddr());
    }

    @ExceptionHandler({IllegalArgumentException.class, NoSuchAdminUserException.class})
    public ResponseEntity<String> handleBadRequest(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}