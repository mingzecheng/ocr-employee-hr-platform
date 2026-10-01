package com.hrplatform.archive.statistics;

import com.hrplatform.common.security.DataScope;
import com.hrplatform.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatisticsControllerTest {
    private final StatisticsService service = mock(StatisticsService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new StatisticsController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void exposesOverviewWithCommonResponseEnvelope() throws Exception {
        when(service.overview(org.mockito.ArgumentMatchers.any(DataScope.class)))
                .thenReturn(new StatisticsDtos.OverviewData(
                10L, 18L, 8L, 6L, 2L, 0.75, 0.7, 3L));

        mockMvc.perform(get("/api/statistics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.activeEmployeeCount").value(10))
                .andExpect(jsonPath("$.data.ocrSuccessRate").value(0.75))
                .andExpect(jsonPath("$.data.archiveCompletenessRate").value(0.7));
    }
}
