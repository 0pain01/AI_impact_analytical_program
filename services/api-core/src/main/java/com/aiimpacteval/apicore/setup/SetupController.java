package com.aiimpacteval.apicore.setup;

import com.aiimpacteval.apicore.setup.SetupQueryService.SetupStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Onboarding checklist and time-to-value instrumentation for admins/eng leaders (PRD E1-S5). */
@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {

    private final SetupQueryService queryService;

    public SetupController(SetupQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/status")
    public SetupStatus getStatus() {
        return queryService.getStatus();
    }
}
