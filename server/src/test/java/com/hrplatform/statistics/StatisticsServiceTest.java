package com.hrplatform.statistics;

import com.hrplatform.common.security.DataScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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

        org.mockito.Mockito.verify(mapper).aggregate("DEPARTMENT", null, 9L, null, null);
    }
}
