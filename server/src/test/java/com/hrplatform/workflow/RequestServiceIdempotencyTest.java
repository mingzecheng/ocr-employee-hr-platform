package com.hrplatform.workflow;

import com.hrplatform.common.idempotency.IdempotentReplayException;
import com.hrplatform.common.idempotency.IdempotencyService;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.employee.EmployeeMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestServiceIdempotencyTest {

    private final RequestMapper mapper = mock(RequestMapper.class);
    @SuppressWarnings("unchecked")
    private final EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
    private final IdempotencyService idempotency = mock(IdempotencyService.class);

    private RequestService service() {
        return new RequestService(mapper, employeeMapper, null, null,
                new EmployeeChangeService(mapper, employeeMapper), null, idempotency);
    }

    private HrRequest pendingDeptApproval() {
        return new HrRequest(10L, "HR-001", "ONBOARDING", 7L, 9L, 9L, 20L, 9L, 2L,
                "{}", "PENDING_DEPT_APPROVAL", "DEPARTMENT", 1, LocalDateTime.now(), null, null,
                null, null, null, null, java.time.LocalDate.now(), "FIXED_TERM", null, null, null, null);
    }

    private RequestActor manager() {
        return new RequestActor(9L, Set.of("DEPT_MANAGER"),
                new DataScope(DataScope.Type.DEPARTMENT, 9L, null, 9L));
    }

    @Test
    void approveRejectedByIdempotencyDoesNotChangeState() {
        when(mapper.findByIdWithScope(10L, null, 9L, DataScope.Type.DEPARTMENT.name()))
                .thenReturn(pendingDeptApproval());
        when(idempotency.tryAcquire(eq("idem:approve:9:10"), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> service().approve(10L, manager(), "同意"))
                .isInstanceOf(IdempotentReplayException.class)
                .hasMessageContaining("请勿重复提交");

        verify(mapper, never()).updateState(any());
    }

    @Test
    void rejectRejectedByIdempotencyDoesNotChangeState() {
        when(mapper.findByIdWithScope(10L, null, 9L, DataScope.Type.DEPARTMENT.name()))
                .thenReturn(pendingDeptApproval());
        when(idempotency.tryAcquire(eq("idem:reject:9:10"), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> service().reject(10L, manager(), "理由"))
                .isInstanceOf(IdempotentReplayException.class)
                .hasMessageContaining("请勿重复提交");

        verify(mapper, never()).updateState(any());
    }

    @Test
    void acquireIsBoundedToTenSecondWindow() {
        when(mapper.findByIdWithScope(10L, null, 9L, DataScope.Type.DEPARTMENT.name()))
                .thenReturn(pendingDeptApproval());
        when(idempotency.tryAcquire(eq("idem:approve:9:10"), eq(Duration.ofSeconds(10)))).thenReturn(false);

        assertThatThrownBy(() -> service().approve(10L, manager(), "同意"))
                .isInstanceOf(IdempotentReplayException.class);
    }
}
