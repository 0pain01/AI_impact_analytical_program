package com.aiimpacteval.apicore.personal;

import com.aiimpacteval.apicore.personal.PersonalDtos.PersonalActivityResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Personal Activity tab (IC role, PRD persona table). Under {@code /api/v1/personal/**} —
 * separate from {@code /api/v1/metrics/**}, which IC is (deliberately) denied on
 * (SecurityConfig). This route is open to any authenticated role instead of role-gated, because
 * it's inherently self-scoped: there's no version of this endpoint that returns anyone's data
 * but the caller's own, so restricting which roles can see their own PRs would add nothing.
 */
@RestController
@RequestMapping("/api/v1/personal")
public class PersonalController {

    private final PersonalQueryService queryService;

    public PersonalController(PersonalQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/activity")
    public PersonalActivityResponse activity(Authentication auth) {
        return queryService.activity(auth.getName());
    }
}