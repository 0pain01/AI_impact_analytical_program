package com.aiimpacteval.connector.aitelemetry.backfill;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal triggers, not exposed publicly — same shape as every other connector's
 * {@code /internal/backfill}. Invoked by api-core, or directly for onboarding/debugging.
 */
@RestController
public class BackfillController {

    private final ClaudeCodeUsageBackfillService claudeCodeUsageBackfillService;
    private final CopilotUsageBackfillService copilotUsageBackfillService;

    public BackfillController(ClaudeCodeUsageBackfillService claudeCodeUsageBackfillService,
                              CopilotUsageBackfillService copilotUsageBackfillService) {
        this.claudeCodeUsageBackfillService = claudeCodeUsageBackfillService;
        this.copilotUsageBackfillService = copilotUsageBackfillService;
    }

    @PostMapping("/internal/backfill/claude-code")
    public ClaudeCodeUsageBackfillService.BackfillResult backfillClaudeCode() {
        return claudeCodeUsageBackfillService.backfill();
    }

    @PostMapping("/internal/backfill/copilot")
    public CopilotUsageBackfillService.BackfillResult backfillCopilot() {
        return copilotUsageBackfillService.backfill();
    }
}
