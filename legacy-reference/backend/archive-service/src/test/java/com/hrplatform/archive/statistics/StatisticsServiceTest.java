package com.hrplatform.archive.statistics;

import com.hrplatform.common.security.DataScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class StatisticsServiceTest {
    private static final DataScope ADMIN_SCOPE = DataScope.all(0L);
    private final StatisticsMapper mapper = mock(StatisticsMapper.class);
    private final StatisticsService service = new StatisticsService(mapper);

    @Test
    void calculatesRatesFromArchiveAggregates() {
        when(mapper.aggregateInScope("ALL", null, null)).thenReturn(new StatisticsMapper.StatisticsAggregate(
                10L, 18L, 8L, 6L, 2L, 3L, 7L));

        StatisticsDtos.OverviewData result = service.overview(ADMIN_SCOPE);

        assertThat(result.activeEmployeeCount()).isEqualTo(10L);
        assertThat(result.archiveDocumentCount()).isEqualTo(18L);
        assertThat(result.ocrTaskCount()).isEqualTo(8L);
        assertThat(result.ocrSucceededCount()).isEqualTo(6L);
        assertThat(result.ocrFailedCount()).isEqualTo(2L);
        assertThat(result.ocrSuccessRate()).isEqualTo(0.75);
        assertThat(result.archiveCompletenessRate()).isEqualTo(0.7);
        assertThat(result.pendingReviewCount()).isEqualTo(3L);
    }

    @Test
    void returnsZeroRatesWhenThereAreNoEmployeesOrOcrTasks() {
        when(mapper.aggregateInScope("ALL", null, null)).thenReturn(new StatisticsMapper.StatisticsAggregate(
                0L, 0L, 0L, 0L, 0L, 0L, 0L));

        StatisticsDtos.OverviewData result = service.overview(ADMIN_SCOPE);

        assertThat(result.archiveCompletenessRate()).isZero();
        assertThat(result.ocrSuccessRate()).isZero();
    }

    @Test
    void passesTheCurrentScopeToEveryStatisticsAggregate() {
        DataScope scope = DataScope.department(12L, 7L);
        when(mapper.aggregateInScope("DEPARTMENT", null, 12L))
                .thenReturn(new StatisticsMapper.StatisticsAggregate(2L, 3L, 2L, 1L, 1L, 1L, 1L));

        assertThat(service.overview(scope).activeEmployeeCount()).isEqualTo(2L);
        verify(mapper).aggregateInScope("DEPARTMENT", null, 12L);
    }

    @Test
    void emptyScopeCannotReadGlobalStatistics() {
        assertThat(service.overview(DataScope.none(99L)).activeEmployeeCount()).isZero();
        verifyNoInteractions(mapper);
    }
}
