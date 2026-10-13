package com.hrplatform.access;

import java.time.LocalDateTime;

public record ArchiveAccessResolution(Long id, Long applicationId, Long resolvedByUserId,
                                      String resolvedByName, String resolutionNote,
                                      LocalDateTime resolvedAt) {
}
