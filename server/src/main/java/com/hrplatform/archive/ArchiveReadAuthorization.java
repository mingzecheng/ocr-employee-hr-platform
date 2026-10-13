package com.hrplatform.archive;

import com.hrplatform.common.security.DataScope;

@FunctionalInterface
public interface ArchiveReadAuthorization {
    boolean allowsRead(Long versionId, Long uploadedByUserId, DataScope scope);
}
