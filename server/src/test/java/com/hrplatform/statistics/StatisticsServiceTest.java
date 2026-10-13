package com.hrplatform.statistics;

import com.hrplatform.common.security.DataScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {
    @Mock
    private StatisticsMapper mapper;

    @Test
    void zeroArchiveDenominatorProducesZeroCompleteness() {
        when(mapper.aggregate("DEPARTMENT", null, 9L, null, null))
                .thenReturn(new StatisticsRaw(8L, 0L, 12L, 0L, 3L, 1L, 2L, 1L, 0L, 0L, 0L));

        StatisticsOverview overview = new StatisticsService(mapper).overview(
                new DataScope(DataScope.Type.DEPARTMENT, 20L, null, 9L), null, null);

        assertThat(overview.employeeCount()).isEqualTo(8L);
        assertThat(overview.archiveCompleteness()).isZero();
        assertThat(overview.averageOcrDurationMs()).isZero();
    }

    @Test
    void departmentScopeIsPassedToAggregateQuery() {
        when(mapper.aggregate("DEPARTMENT", null, 9L, null, null)).thenReturn(StatisticsRaw.empty());

        new StatisticsService(mapper).overview(new DataScope(DataScope.Type.DEPARTMENT, 20L, null, 9L), null, null);

        verify(mapper).aggregate("DEPARTMENT", null, 9L, null, null);
    }

    @Test
    void reportKeepsAggregateSectionsAndPassesDataScope() {
        when(mapper.monthlyTrend("DEPARTMENT", null, 9L, null, null)).thenReturn(
                java.util.List.of(new StatisticsTrendPoint("2026-10", 4, 3, 2, 1)));
        when(mapper.departments("DEPARTMENT", null, 9L, null, null)).thenReturn(
                java.util.List.of(new DepartmentStatisticsRow(9L, "研发部", 4, 4, 3, 1, 0)));
        when(mapper.workflowCycle("DEPARTMENT", null, 9L, null, null)).thenReturn(
                new WorkflowCycleStatistics(2, 12.5, 2, 24));

        StatisticsReport report = new StatisticsService(mapper).report(
                new DataScope(DataScope.Type.DEPARTMENT, 20L, null, 9L), null, null);

        assertThat(report.monthlyTrend()).hasSize(1);
        assertThat(report.departments().get(0).departmentName()).isEqualTo("研发部");
        assertThat(report.workflowCycle().averageDurationHours()).isEqualTo(12.5);
        verify(mapper).workflowCycle("DEPARTMENT", null, 9L, null, null);
    }
}
