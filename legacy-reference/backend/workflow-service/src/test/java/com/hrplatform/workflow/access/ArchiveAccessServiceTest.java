package com.hrplatform.workflow.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchiveAccessServiceTest {
    private final ArchiveAccessMapper mapper = mock(ArchiveAccessMapper.class);
    private final ArchiveAccessService service = new ArchiveAccessService(mapper, new ObjectMapper());

    @Test
    void createsDraftWithImmutableScopeAndApplicant() {
        when(mapper.insertApplication(any(ArchiveAccessApplication.class))).thenAnswer(invocation -> {
            ArchiveAccessApplication application = invocation.getArgument(0);
            application.setId(101L);
            return 1;
        });

        ArchiveAccessDtos.AccessData result = service.create(createRequest(), 7L);

        assertThat(result.id()).isEqualTo(101L);
        assertThat(result.applicationNo()).startsWith("AA-");
        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.applicantId()).isEqualTo(7L);
        verify(mapper).insertItem(any(ArchiveAccessItem.class));
    }

    @Test
    void requiresDepartmentApprovalBeforeHrApproval() {
        ArchiveAccessApplication application = application("PENDING_DEPT_APPROVAL", "DEPT_APPROVAL");
        when(mapper.findApplicationById(101L)).thenReturn(application);

        ArchiveAccessDtos.AccessData next = service.approve(101L, 8L, true, "部门同意");

        assertThat(next.status()).isEqualTo("PENDING_HR_APPROVAL");
        assertThat(next.currentNode()).isEqualTo("HR_APPROVAL");
        verify(mapper).insertApproval(any(ArchiveAccessApproval.class));

        application.setStatus("DRAFT");
        application.setCurrentNode("DRAFT");
        assertThatThrownBy(() -> service.approve(101L, 9L, true, "人事同意"))
                .isInstanceOf(ArchiveAccessStateException.class)
                .hasMessageContaining("PENDING_DEPT_APPROVAL");
    }

    @Test
    void checkoutRequiresApprovedApplicationAndReturnRecordsNormalClosure() {
        ArchiveAccessApplication application = application("APPROVED", "COMPLETED");
        when(mapper.findApplicationById(101L)).thenReturn(application);
        when(mapper.findUseByApplicationId(101L)).thenReturn(null);
        when(mapper.insertUse(any(ArchiveUseRecord.class))).thenAnswer(invocation -> {
            ArchiveUseRecord use = invocation.getArgument(0);
            use.setId(202L);
            return 1;
        });
        when(mapper.findUseById(202L)).thenAnswer(invocation -> useRecord(202L, 101L, "IN_USE"));

        ArchiveAccessDtos.UseData checkedOut = service.checkout(101L, 7L);
        ArchiveAccessDtos.UseData returned = service.returnUse(202L, 7L,
                new ArchiveAccessDtos.ReturnRequest("NORMAL", null, null, null));

        assertThat(checkedOut.status()).isEqualTo("IN_USE");
        assertThat(returned.status()).isEqualTo("RETURNED");
        verify(mapper).updateApplicationState(eq(101L), eq("RETURNED"), eq("COMPLETED"), any(), any());
    }

    @Test
    void missingOrDamagedMaterialEndsInAbnormalState() {
        ArchiveAccessApplication application = application("APPROVED", "COMPLETED");
        application.setDueAt(LocalDateTime.now().plusDays(1));
        when(mapper.findApplicationById(101L)).thenReturn(application);
        when(mapper.findUseByApplicationId(101L)).thenReturn(null);
        when(mapper.insertUse(any(ArchiveUseRecord.class))).thenAnswer(invocation -> {
            ArchiveUseRecord use = invocation.getArgument(0);
            use.setId(202L);
            return 1;
        });
        when(mapper.findUseById(202L)).thenReturn(useRecord(202L, 101L, "IN_USE"));

        service.checkout(101L, 7L);
        ArchiveAccessDtos.UseData returned = service.returnUse(202L, 7L,
                new ArchiveAccessDtos.ReturnRequest("NORMAL", null, "身份证缺失", ""));

        assertThat(returned.status()).isEqualTo("ABNORMAL");
        verify(mapper).updateUseReturn(eq(202L), eq("ABNORMAL"), any(), eq(null), eq("NORMAL"),
                eq("身份证缺失"), eq(null));
        verify(mapper).updateApplicationState(eq(101L), eq("ABNORMAL"), eq("ABNORMAL"), any(), any());
    }

    @Test
    void overdueUseEndsInAbnormalStateEvenWithoutMissingOrDamage() {
        ArchiveAccessApplication application = application("APPROVED", "COMPLETED");
        when(mapper.findApplicationById(101L)).thenReturn(application);
        when(mapper.findUseByApplicationId(101L)).thenReturn(null);
        when(mapper.insertUse(any(ArchiveUseRecord.class))).thenAnswer(invocation -> {
            ArchiveUseRecord use = invocation.getArgument(0);
            use.setId(202L);
            return 1;
        });
        ArchiveUseRecord overdue = useRecord(202L, 101L, "IN_USE");
        overdue.setDueAt(LocalDateTime.now().minusMinutes(1));
        when(mapper.findUseById(202L)).thenReturn(overdue);

        service.checkout(101L, 7L);
        ArchiveAccessDtos.UseData returned = service.returnUse(202L, 7L,
                new ArchiveAccessDtos.ReturnRequest("NORMAL", null, null, null));

        assertThat(returned.status()).isEqualTo("ABNORMAL");
        verify(mapper).updateApplicationState(eq(101L), eq("ABNORMAL"), eq("ABNORMAL"), any(), any());
    }

    private ArchiveAccessDtos.CreateRequest createRequest() {
        return new ArchiveAccessDtos.CreateRequest(42L, "PAPER_BORROW", "入职核验",
                LocalDateTime.now(), LocalDateTime.now().plusDays(2), true,
                List.of(new ArchiveAccessDtos.ItemRequest(501L, 601L, "FULL")));
    }

    private ArchiveAccessApplication application(String status, String node) {
        ArchiveAccessApplication application = new ArchiveAccessApplication();
        application.setId(101L);
        application.setApplicationNo("AA-20260917-TEST");
        application.setApplicantId(7L);
        application.setEmployeeId(42L);
        application.setUseType("PAPER_BORROW");
        application.setPurpose("入职核验");
        application.setStartAt(LocalDateTime.now().minusMinutes(1));
        application.setDueAt(LocalDateTime.now().plusDays(1));
        application.setReturnRequired(true);
        application.setStatus(status);
        application.setCurrentNode(node);
        return application;
    }

    private ArchiveUseRecord useRecord(long id, long applicationId, String status) {
        ArchiveUseRecord use = new ArchiveUseRecord();
        use.setId(id);
        use.setApplicationId(applicationId);
        use.setReceiverId(7L);
        use.setStatus(status);
        use.setDueAt(LocalDateTime.now().plusDays(1));
        return use;
    }
}
