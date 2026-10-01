package com.hrplatform.workflow.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestServiceTest {
    private final RequestMapper mapper = mock(RequestMapper.class);
    private final RequestService service = new RequestService(mapper, new ObjectMapper());

    @Test
    void createsDraftWithUniqueRequestNumberAndApplicant() {
        when(mapper.insert(any(HrRequest.class))).thenAnswer(invocation -> {
            HrRequest request = invocation.getArgument(0);
            request.setId(11L);
            return 1;
        });

        RequestDtos.RequestData result = service.create(
                new RequestDtos.CreateRequest("ONBOARDING", 42L,
                        new ObjectMapper().createObjectNode().put("source", "ocr")), 7L);

        assertThat(result.id()).isEqualTo(11L);
        assertThat(result.requestNo()).startsWith("HR-");
        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.applicantId()).isEqualTo(7L);
        verify(mapper).insert(any(HrRequest.class));
    }

    @Test
    void submitsDraftAndMovesItToDepartmentApproval() {
        HrRequest request = request("DRAFT", "DRAFT");
        when(mapper.findById(11L)).thenReturn(request);

        RequestDtos.RequestData result = service.submit(11L, 7L);

        assertThat(result.status()).isEqualTo("PENDING_DEPT_APPROVAL");
        verify(mapper).updateState(eq(11L), eq("PENDING_DEPT_APPROVAL"), eq("DEPT_APPROVAL"), any(), eq(null));
        verify(mapper).insertAudit(any(WorkflowAuditLog.class));
    }

    @Test
    void rejectsIllegalApprovalState() {
        when(mapper.findById(11L)).thenReturn(request("DRAFT", "DRAFT"));

        assertThatThrownBy(() -> service.approve(11L, 7L, true, "通过"))
                .isInstanceOf(RequestStateException.class)
                .hasMessageContaining("PENDING_DEPT_APPROVAL");
    }

    private HrRequest request(String status, String currentNode) {
        HrRequest request = new HrRequest();
        request.setId(11L);
        request.setRequestNo("HR-20260917-TEST");
        request.setRequestType("ONBOARDING");
        request.setEmployeeId(42L);
        request.setApplicantId(7L);
        request.setStatus(status);
        request.setCurrentNode(currentNode);
        return request;
    }
}
