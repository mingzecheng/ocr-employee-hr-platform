package com.hrplatform.access;

import com.hrplatform.common.security.JwtPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class ArchiveAccessDecisionControllerTest {
    @Test
    void rejectionEndpointAcceptsAReasonAndReturnsUpdatedDecision() throws Exception {
        ArchiveAccessService service = mock(ArchiveAccessService.class);
        when(service.reject(eq(41L), eq("材料用途与授权范围不符"), any())).thenReturn(
                new ArchiveAccessApplication(41L, "ACC-41", 20L, 9L, "档案查阅", "READ",
                        java.time.LocalDateTime.now(), java.time.LocalDateTime.now().plusDays(1),
                        "REJECTED", 2, java.time.LocalDateTime.now()));
        ArchiveAccessController controller = new ArchiveAccessController(service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var authentication = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(7L, 8L, 9L, Set.of("HR_ADMIN")), null,
                List.of(new SimpleGrantedAuthority("ROLE_HR_ADMIN")));

        mvc.perform(post("/api/archive-access/41/reject")
                        .principal(authentication)
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"材料用途与授权范围不符\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        verify(service).reject(eq(41L), eq("材料用途与授权范围不符"), any());
    }
}
