package com.hrplatform.access;

import com.hrplatform.common.security.DataScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class ArchiveReadAuthorizationPolicyTest {
    private final AccessMapper mapper = mock(AccessMapper.class);
    private final ArchiveReadAuthorizationPolicy policy = new ArchiveReadAuthorizationPolicy(mapper);

    @Test
    void uploadedVersionIsReadableByItsUploaderForArchiveMaintenance() {
        DataScope hr = new DataScope(DataScope.Type.ALL, 12L, null, null);

        assertThat(policy.allowsRead(20L, 12L, hr)).isTrue();

        verify(mapper, never()).countActiveReadGrants(any(), any(), eq(true), any());
    }

    @Test
    void departmentManagerRequiresAnActiveApplicationGrant() {
        DataScope manager = new DataScope(DataScope.Type.DEPARTMENT, 45L, null, 10L);
        when(mapper.countActiveReadGrants(eq(20L), eq(45L), eq(false), any())).thenReturn(1);

        assertThat(policy.allowsRead(20L, 12L, manager)).isTrue();
    }

    @Test
    void hrMayReadMaterialWhileReviewingItsPendingApplication() {
        DataScope hr = new DataScope(DataScope.Type.ALL, 30L, null, null);
        when(mapper.countActiveReadGrants(eq(20L), eq(30L), eq(true), any())).thenReturn(1);

        assertThat(policy.allowsRead(20L, 12L, hr)).isTrue();
    }

    @Test
    void missingGrantDeniesRead() {
        DataScope employee = new DataScope(DataScope.Type.EMPLOYEE, 45L, 7L, 10L);

        assertThat(policy.allowsRead(20L, 12L, employee)).isFalse();
    }
}
