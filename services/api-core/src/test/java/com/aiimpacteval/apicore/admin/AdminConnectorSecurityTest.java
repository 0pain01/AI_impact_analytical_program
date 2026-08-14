package com.aiimpacteval.apicore.admin;

import com.aiimpacteval.apicore.admin.AdminConnectorService.ConnectorHealth;
import com.aiimpacteval.apicore.security.JwtConfig;
import com.aiimpacteval.apicore.security.Role;
import com.aiimpacteval.apicore.security.SecurityConfig;
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

/** The Admin console's connector-health endpoint is ADMIN-only (E1-S4/E8). */
@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class AdminConnectorSecurityTest {

    @TestConfiguration
    static class Stubs {
        @Bean
        AdminConnectorService adminConnectorService() {
            return new AdminConnectorService(null) {
                @Override
                public List<ConnectorHealth> listConnectors() {
                    return List.of();
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCanReadConnectors() throws Exception {
        mockMvc.perform(get("/api/v1/admin/connectors").with(jwt().authorities(new SimpleGrantedAuthority(Role.ADMIN.authority()))))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdminIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/connectors").with(jwt().authorities(new SimpleGrantedAuthority(Role.ENG_LEADER.authority()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/connectors")).andExpect(status().isUnauthorized());
    }
}
