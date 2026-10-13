package com.hrplatform.ocr;

import com.hrplatform.archive.ArchiveCatalog;
import com.hrplatform.archive.ArchiveOcrSource;
import com.hrplatform.archive.ArchiveReadAuthorization;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.common.security.JwtPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcrConfirmControllerTest {
    private final OcrService ocrService = mock(OcrService.class);
    private final ArchiveCatalog archiveCatalog = mock(ArchiveCatalog.class);
    private final OcrBindingMapper bindingMapper = mock(OcrBindingMapper.class);
    private final ArchiveReadAuthorization readAuthorization = mock(ArchiveReadAuthorization.class);
    private final OcrConfirmService confirmService = mock(OcrConfirmService.class);
    private final OcrController controller = new OcrController(ocrService, archiveCatalog, bindingMapper,
            readAuthorization, confirmService);

    private UsernamePasswordAuthenticationToken hrAdmin() {
        JwtPrincipal principal = new JwtPrincipal(3L, 99L, 10L, Set.of("HR_ADMIN"));
        return new UsernamePasswordAuthenticationToken(principal, "token");
    }

    private OcrBinding binding() {
        return new OcrBinding(7L, 50L, "task-7", "SUCCEEDED", "paddle", 10L, false, null,
                LocalDateTime.now(), "employee_profile");
    }

    @Test
    void confirmDelegatesToServiceAfterScopeCheck() {
        when(bindingMapper.findByIdWithScope(7L, 99L, 10L, DataScope.Type.ALL.name())).thenReturn(binding());
        when(archiveCatalog.findOcrSourceByVersionIdWithScope(50L, 99L, 10L, DataScope.Type.ALL.name()))
                .thenReturn(new ArchiveOcrSource(50L, "archive/50/a.png", "a.png", "image/png",
                        1L, "hash", "employee_profile", 99L, 10L, 1L));
        when(readAuthorization.allowsRead(eq(50L), eq(1L), any())).thenReturn(true);
        when(confirmService.confirm(eq(7L), eq(3L), eq("已核对"), any()))
                .thenReturn(new OcrConfirmResult(7L, "CONFIRMED", 99L, true));

        var response = controller.confirm(7L, new OcrController.ConfirmRequest("已核对"), hrAdmin());

        assertThat(response.data().confirmStatus()).isEqualTo("CONFIRMED");
        verify(confirmService).confirm(eq(7L), eq(3L), eq("已核对"), any());
    }

    @Test
    void rejectDelegatesToServiceAfterScopeCheck() {
        when(bindingMapper.findByIdWithScope(7L, 99L, 10L, DataScope.Type.ALL.name())).thenReturn(binding());
        when(archiveCatalog.findOcrSourceByVersionIdWithScope(50L, 99L, 10L, DataScope.Type.ALL.name()))
                .thenReturn(new ArchiveOcrSource(50L, "archive/50/a.png", "a.png", "image/png",
                        1L, "hash", "employee_profile", 99L, 10L, 1L));
        when(readAuthorization.allowsRead(eq(50L), eq(1L), any())).thenReturn(true);
        when(confirmService.reject(eq(7L), eq(3L), eq("图片模糊"), any()))
                .thenReturn(new OcrConfirmResult(7L, "REJECTED", null, false));

        var response = controller.reject(7L, new OcrController.ConfirmRequest("图片模糊"), hrAdmin());

        assertThat(response.data().confirmStatus()).isEqualTo("REJECTED");
        verify(confirmService).reject(eq(7L), eq(3L), eq("图片模糊"), any());
    }

    @Test
    void confirmWithoutBindingThrowsNotFoundAndDoesNotCallService() {
        when(bindingMapper.findByIdWithScope(7L, 99L, 10L, DataScope.Type.ALL.name())).thenReturn(null);

        assertThatThrownBy(() -> controller.confirm(7L, new OcrController.ConfirmRequest("x"), hrAdmin()))
                .isInstanceOf(OcrBindingNotFoundException.class);

        org.mockito.Mockito.verifyNoInteractions(confirmService);
    }
}
