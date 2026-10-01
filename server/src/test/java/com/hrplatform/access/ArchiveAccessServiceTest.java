package com.hrplatform.access;

import com.hrplatform.archive.ArchiveMapper;
import com.hrplatform.archive.ArchiveOcrSource;
import com.hrplatform.common.security.DataScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveAccessServiceTest {
    @Mock
    private AccessMapper mapper;
    @Mock
    private ArchiveMapper archiveMapper;

    @Test
    void creatingApplicationRejectsVersionOutsideActorScope() {
        when(archiveMapper.findOcrSourceByVersionIdWithScope(3L, null, 9L, DataScope.Type.DEPARTMENT.name()))
                .thenReturn(null);
        AccessActor actor = new AccessActor(20L, Set.of("EMPLOYEE"),
                new DataScope(DataScope.Type.DEPARTMENT, 20L, null, 9L));
        ArchiveAccessService service = new ArchiveAccessService(mapper, archiveMapper);

        assertThatThrownBy(() -> service.create(new AccessCreateRequest("查阅", "READ",
                        LocalDateTime.now(), LocalDateTime.now().plusDays(2), List.of(3L)), actor))
                .isInstanceOf(ArchiveAccessStateException.class);
    }
}
