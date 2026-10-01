package com.hrplatform.workflow.scope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.workflow.authorization.WorkflowAuthorizationClient;
import com.hrplatform.workflow.request.HrRequest;
import com.hrplatform.workflow.request.RequestDtos;
import com.hrplatform.workflow.request.RequestMapper;
import com.hrplatform.workflow.request.RequestService;
import com.hrplatform.workflow.security.WorkflowActor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowDataScopeTest {
    private final RequestMapper mapper = mock(RequestMapper.class);
    private final WorkflowAuthorizationClient authorizationClient = mock(WorkflowAuthorizationClient.class);
    private final RequestService service = new RequestService(mapper, new ObjectMapper(), authorizationClient);

    @Test
    void createsRequestWithArchiveReturnedDepartmentSnapshot() {
        when(authorizationClient.authorizeEmployee(42L, "token"))
                .thenReturn(new WorkflowAuthorizationClient.AuthorizedResource(42L, 9L));
        when(mapper.insert(any(HrRequest.class))).thenAnswer(invocation -> {
            HrRequest request = invocation.getArgument(0);
            request.setId(11L);
            return 1;
        });

        service.create(new RequestDtos.CreateRequest("ONBOARDING", 42L, null),
                new WorkflowActor(7L, DataScope.department(9L, 7L), "token"));

        ArgumentCaptor<HrRequest> captor = ArgumentCaptor.forClass(HrRequest.class);
        verify(mapper).insert(captor.capture());
        HrRequest stored = captor.getValue();
        assertThat(stored.getTargetDepartmentId()).isEqualTo(9L);
    }

    @Test
    void readsOnlyRequestsInsideCurrentDepartmentScope() {
        HrRequest request = new HrRequest();
        request.setId(11L);
        request.setEmployeeId(42L);
        request.setTargetDepartmentId(9L);
        request.setPayloadJson("{}");
        when(mapper.findById(11L, "DEPARTMENT", null, 9L)).thenReturn(request);

        service.get(11L, new WorkflowActor(7L, DataScope.department(9L, 7L), "token"));

        verify(mapper).findById(11L, "DEPARTMENT", null, 9L);
    }
}
