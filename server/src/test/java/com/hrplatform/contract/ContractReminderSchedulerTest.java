package com.hrplatform.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractReminderSchedulerTest {

    @Mock
    private ContractReminderMapper mapper;

    private ContractReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ContractReminderScheduler(mapper);
    }

    @Test
    void generates30And7DayStagesButNot1DayForContractDueInFiveDays() {
        LocalDate today = LocalDate.now();
        when(mapper.findDueContractsWithin(any(), any()))
                .thenReturn(List.of(new ContractReminderMapper.DueContractRow(7L, today.plusDays(5))));
        when(mapper.exists(7L, "30D")).thenReturn(false);
        when(mapper.exists(7L, "7D")).thenReturn(false);
        when(mapper.insert(anyLong(), any(), anyString())).thenReturn(1);

        int created = scheduler.generateDueReminders();

        assertThat(created).isEqualTo(2);
        verify(mapper).insert(7L, today.plusDays(5), "30D");
        verify(mapper).insert(7L, today.plusDays(5), "7D");
        verify(mapper, never()).insert(eq(7L), any(), eq("1D"));
    }

    @Test
    void skipsStagesAlreadyGeneratedAcrossWindows() {
        LocalDate today = LocalDate.now();
        when(mapper.findDueContractsWithin(any(), any()))
                .thenReturn(List.of(new ContractReminderMapper.DueContractRow(9L, today.plusDays(2))));
        when(mapper.exists(9L, "30D")).thenReturn(true);   // 上次扫描已生成 30D
        when(mapper.exists(9L, "7D")).thenReturn(false);
        when(mapper.insert(anyLong(), any(), anyString())).thenReturn(1);

        int created = scheduler.generateDueReminders();

        // 只补 7D；30D 幂等跳过；2 天 > 1 天不生成 1D
        assertThat(created).isEqualTo(1);
        verify(mapper, never()).insert(eq(9L), any(), eq("30D"));
        verify(mapper).insert(9L, today.plusDays(2), "7D");
        verify(mapper, never()).insert(eq(9L), any(), eq("1D"));
    }

    @Test
    void generatesAllThreeStagesForContractDueToday() {
        LocalDate today = LocalDate.now();
        when(mapper.findDueContractsWithin(any(), any()))
                .thenReturn(List.of(new ContractReminderMapper.DueContractRow(3L, today)));
        when(mapper.exists(anyLong(), anyString())).thenReturn(false);
        when(mapper.insert(anyLong(), any(), anyString())).thenReturn(1);

        int created = scheduler.generateDueReminders();

        assertThat(created).isEqualTo(3);
        verify(mapper).insert(3L, today, "30D");
        verify(mapper).insert(3L, today, "7D");
        verify(mapper).insert(3L, today, "1D");
    }
}
