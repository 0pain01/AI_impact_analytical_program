package com.aiimpacteval.apicore.audit;

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

/** The audit log is ADMIN-only (E8-S3). */
@WebMvcTest(AuditController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class AuditSecurityTest {

    @TestConfiguration
    static class Stubs {
        @Bean
        AuditQueryService auditQueryService() {
            return new AuditQueryService(null) {
                @Override
                public List<AuditEntry> recent(int limit) {
                    return List.of();
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCanReadAudit() throws Exception {
        mockMvc.perform(get("/api/v1/audit").with(jwt().authorities(new SimpleGrantedAuthority(Role.ADMIN.authority()))))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdminIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/audit").with(jwt().authorities(new SimpleGrantedAuthority(Role.ENG_LEADER.authority()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/audit")).andExpect(status().isUnauthorized());
    }
}
