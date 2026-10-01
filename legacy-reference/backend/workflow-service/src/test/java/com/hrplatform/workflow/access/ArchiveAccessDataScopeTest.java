package com.hrplatform.workflow.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.workflow.authorization.WorkflowAuthorizationClient;
import com.hrplatform.workflow.authorization.WorkflowDataScopeDeniedException;
import com.hrplatform.workflow.security.WorkflowActor;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchiveAccessDataScopeTest {
    private final ArchiveAccessMapper mapper = mock(ArchiveAccessMapper.class);
    private final WorkflowAuthorizationClient authorizationClient = mock(WorkflowAuthorizationClient.class);
    private final ArchiveAccessService service = new ArchiveAccessService(mapper, new ObjectMapper(), authorizationClient);
    private final WorkflowActor manager = new WorkflowActor(7L, DataScope.department(9L, 7L), "token");

    @Test
    void persistsDepartmentReturnedByArchiveAuthorization() {
        when(authorizationClient.authorizeEmployee(42L, "token"))
                .thenReturn(new WorkflowAuthorizationClient.AuthorizedResource(42L, 9L));
        when(authorizationClient.authorizeVersion(601L, "token"))
                .thenReturn(new WorkflowAuthorizationClient.AuthorizedResource(42L, 9L, 501L));
        when(mapper.insertApplication(any(ArchiveAccessApplication.class))).thenAnswer(invocation -> {
            ArchiveAccessApplication application = invocation.getArgument(0);
            application.setId(101L);
            return 1;
        });

        ArchiveAccessDtos.AccessData result = service.create(request(42L, 501L, 601L), manager);

        assertThat(result.id()).isEqualTo(101L);
        var captor = org.mockito.ArgumentCaptor.forClass(ArchiveAccessApplication.class);
        verify(mapper).insertApplication(captor.capture());
        assertThat(captor.getValue().getTargetDepartmentId()).isEqualTo(9L);
    }

    @Test
    void rejectsVersionBelongingToAnotherEmployeeOrDocument() {
        when(authorizationClient.authorizeEmployee(42L, "token"))
                .thenReturn(new WorkflowAuthorizationClient.AuthorizedResource(42L, 9L));
        when(authorizationClient.authorizeVersion(601L, "token"))
                .thenReturn(new WorkflowAuthorizationClient.AuthorizedResource(88L, 12L, 999L));

        assertThatThrownBy(() -> service.create(request(42L, 501L, 601L), manager))
                .isInstanceOf(WorkflowDataScopeDeniedException.class);
    }

    private ArchiveAccessDtos.CreateRequest request(long employeeId, long documentId, long versionId) {
        return new ArchiveAccessDtos.CreateRequest(employeeId, "PAPER_BORROW", "verification",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1), true,
                List.of(new ArchiveAccessDtos.ItemRequest(documentId, versionId, "FULL")));
    }
}
