package com.hrplatform.contract;

import com.hrplatform.audit.OperationLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractReminderServiceTest {

    @Mock
    private ContractReminderMapper mapper;
    @Mock
    private OperationLogService operationLogService;

    private ContractReminderService service() {
        return new ContractReminderService(mapper, operationLogService);
    }

    @Test
    void ackPendingEventWritesAckedAndAudit() {
        when(mapper.findById(201L)).thenReturn(new ContractReminderEvent(201L, 1L,
                LocalDate.now().plusDays(5), "7D", "PENDING", LocalDateTime.now(), null));
        when(mapper.ack(eq(201L), any())).thenReturn(1);

        service().ack(201L, 4L);

        verify(mapper).ack(eq(201L), any());
        verify(operationLogService).record(eq(4L), eq("ACK_CONTRACT_REMINDER"),
                eq("CONTRACT_REMINDER"), eq(201L), eq("SUCCESS"));
    }

    @Test
    void ackAlreadyAckedEventIsIdempotentNoOp() {
        when(mapper.findById(202L)).thenReturn(new ContractReminderEvent(202L, 1L,
                LocalDate.now(), "1D", "ACKED", LocalDateTime.now(), LocalDateTime.now()));

        service().ack(202L, 4L);

        verify(mapper, never()).ack(any(), any());
        verify(operationLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void ackUnknownEventThrows() {
        when(mapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service().ack(999L, 4L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在");

        verify(mapper, never()).ack(any(), any());
    }
}
