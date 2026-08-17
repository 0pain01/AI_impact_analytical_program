package com.aiimpacteval.apicore.security;

import com.aiimpacteval.apicore.security.DevTokenService.IssuedToken;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev/pilot auth bridge (ADR-0004). Gated by {@code auth.dev-token-enabled} — MUST be false in
 * production, where an OIDC/SSO flow replaces it. Issues a short-lived JWT whose role and team
 * scope come from {@code core.app_user}, not from the caller.
 */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "auth.dev-token-enabled", havingValue = "true", matchIfMissing = true)
public class DevTokenController {

    private final DevTokenService devTokenService;

    public DevTokenController(DevTokenService devTokenService) {
        this.devTokenService = devTokenService;
    }

    @PostMapping("/dev-token")
    public ResponseEntity<IssuedToken> devToken(@RequestParam String email, HttpServletRequest request) {
        return ResponseEntity.ok(devTokenService.issue(email, request.getRemoteAddr()));
    }

    @ExceptionHandler(NoSuchAppUserException.class)
    public ResponseEntity<String> handleNoSuchUser(NoSuchAppUserException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
}