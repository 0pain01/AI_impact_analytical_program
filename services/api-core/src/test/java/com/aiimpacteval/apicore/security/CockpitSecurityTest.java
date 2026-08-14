package com.aiimpacteval.apicore.security;

import com.aiimpacteval.apicore.metrics.CockpitController;
import com.aiimpacteval.apicore.metrics.CockpitDtos.CockpitResponse;
import com.aiimpacteval.apicore.metrics.CockpitQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC enforcement for the Cockpit endpoint (E8-S1). Uses spring-security-test's jwt()
 * post-processor to assert the filter chain, independent of token issuance.
 */
@WebMvcTest(CockpitController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class CockpitSecurityTest {

    @TestConfiguration
    static class Stubs {
        @Bean
        CockpitQueryService cockpitQueryService() {
            return new CockpitQueryService(null) {
                @Override
                public CockpitResponse cockpit(int windowDays, String repo) {
                    return new CockpitResponse(null, windowDays, repo, List.of());
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/cockpit")).andExpect(status().isUnauthorized());
    }

    @Test
    void individualContributorIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/cockpit").with(jwt().authorities(authority(Role.IC))))
                .andExpect(status().isForbidden());
    }

    @Test
    void engineeringLeaderIsAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/cockpit").with(jwt().authorities(authority(Role.ENG_LEADER))))
                .andExpect(status().isOk());
    }

    @Test
    void financeReadonlyIsAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/cockpit").with(jwt().authorities(authority(Role.FINANCE_READONLY))))
                .andExpect(status().isOk());
    }

    private static SimpleGrantedAuthority authority(Role role) {
        return new SimpleGrantedAuthority(role.authority());
    }
}
