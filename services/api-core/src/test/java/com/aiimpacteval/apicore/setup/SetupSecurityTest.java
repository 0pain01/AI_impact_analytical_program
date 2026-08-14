package com.aiimpacteval.apicore.setup;

import com.aiimpacteval.apicore.security.JwtConfig;
import com.aiimpacteval.apicore.security.Role;
import com.aiimpacteval.apicore.security.SecurityConfig;
import com.aiimpacteval.apicore.setup.SetupQueryService.SetupStatus;
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

/** RBAC for the onboarding checklist (E1-S5): ADMIN/ENG_LEADER only, everyone else denied. */
@WebMvcTest(SetupController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class SetupSecurityTest {

    @TestConfiguration
    static class Stubs {
        @Bean
        SetupQueryService setupQueryService() {
            return new SetupQueryService(null) {
                @Override
                public SetupStatus getStatus() {
                    return new SetupStatus(List.of(), null, null, null, null, 30);
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/setup/status")).andExpect(status().isUnauthorized());
    }

    @Test
    void managerIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/setup/status").with(jwt().authorities(new SimpleGrantedAuthority(Role.MANAGER.authority()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void individualContributorIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/setup/status").with(jwt().authorities(new SimpleGrantedAuthority(Role.IC.authority()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminIsAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/setup/status").with(jwt().authorities(new SimpleGrantedAuthority(Role.ADMIN.authority()))))
                .andExpect(status().isOk());
    }

    @Test
    void engLeaderIsAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/setup/status").with(jwt().authorities(new SimpleGrantedAuthority(Role.ENG_LEADER.authority()))))
                .andExpect(status().isOk());
    }
}
