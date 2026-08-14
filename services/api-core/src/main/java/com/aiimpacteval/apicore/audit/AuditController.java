package com.aiimpacteval.apicore.audit;

import com.aiimpacteval.apicore.audit.AuditQueryService.AuditEntry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Audit log retrieval (E8-S3). ADMIN-only — enforced in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private static final int MAX_LIMIT = 500;

    private final AuditQueryService queryService;

    public AuditController(AuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public List<AuditEntry> recent(@RequestParam(defaultValue = "100") int limit) {
        return queryService.recent(Math.min(Math.max(limit, 1), MAX_LIMIT));
    }
}
