package com.aiimpacteval.apicore.teams;

import com.aiimpacteval.apicore.security.JwtConfig;
import com.aiimpacteval.apicore.security.Role;
import com.aiimpacteval.apicore.security.SecurityConfig;
import com.aiimpacteval.apicore.teams.TeamQueryService.TeamSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RBAC for the teams list (E4-S2) mirrors the Cockpit endpoint's analytical-role gate. */
@WebMvcTest(TeamController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class TeamSecurityTest {

    @TestConfiguration
    static class Stubs {
        @Bean
        TeamQueryService teamQueryService() {
            return new TeamQueryService(null) {
                @Override
                public List<TeamSummary> listTeams() {
                    return List.of(new TeamSummary(UUID.randomUUID(), "Platform Team", 2));
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/teams")).andExpect(status().isUnauthorized());
    }

    @Test
    void individualContributorIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/teams").with(jwt().authorities(new SimpleGrantedAuthority(Role.IC.authority()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerIsAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/teams").with(jwt().authorities(new SimpleGrantedAuthority(Role.MANAGER.authority()))))
                .andExpect(status().isOk());
    }
}
