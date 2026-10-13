package com.hrplatform.ocr;

import com.hrplatform.archive.ArchiveMapper;
import com.hrplatform.archive.ArchiveOcrSource;
import com.hrplatform.archive.ArchiveReadAuthorization;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.common.security.JwtPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;

class OcrControllerAuthorizationTest {
    private final OcrService ocrService = mock(OcrService.class);
    private final ArchiveMapper archiveMapper = mock(ArchiveMapper.class);
    private final OcrBindingMapper bindingMapper = mock(OcrBindingMapper.class);
    private final ArchiveReadAuthorization readAuthorization = mock(ArchiveReadAuthorization.class);
    private final OcrController controller = new OcrController(ocrService, archiveMapper, bindingMapper,
            readAuthorization);

    @Test
    void unauthorizedDepartmentManagerCannotTriggerOcrForAnInScopeVersion() {
        JwtPrincipal principal = new JwtPrincipal(45L, null, 10L, java.util.Set.of("DEPT_MANAGER"));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, "token");
        when(archiveMapper.findOcrSourceByVersionIdWithScope(20L, null, 10L, DataScope.Type.DEPARTMENT.name()))
                .thenReturn(new ArchiveOcrSource(20L, "archive/private.png", "private.png", "image/png",
                        100L, "hash", "employee_profile", 7L, 10L, 12L));
        when(readAuthorization.allowsRead(eq(20L), eq(12L), any())).thenReturn(false);

        assertThatThrownBy(() -> controller.trigger(20L, authentication))
                .isInstanceOf(OcrBindingNotFoundException.class);

        verify(ocrService, never()).triggerResult(any());
    }
}
