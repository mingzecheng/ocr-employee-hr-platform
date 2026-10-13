package com.hrplatform.access;

import com.hrplatform.archive.ArchiveReadAuthorization;
import com.hrplatform.common.security.DataScope;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ArchiveReadAuthorizationPolicy implements ArchiveReadAuthorization {
    private final AccessMapper mapper;

    public ArchiveReadAuthorizationPolicy(AccessMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean allowsRead(Long versionId, Long uploadedByUserId, DataScope scope) {
        if (versionId == null || scope == null || scope.userId() == null) {
            return false;
        }
        if (scope.type() == DataScope.Type.ALL && scope.userId().equals(uploadedByUserId)) {
            return true;
        }
        return mapper.countActiveReadGrants(versionId, scope.userId(), scope.type() == DataScope.Type.ALL,
                LocalDateTime.now()) > 0;
    }
}
