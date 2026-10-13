package com.hrplatform.access;

import java.time.LocalDateTime;

public record ArchiveAccessDecision(Long id, Long applicationId, Long decidedByUserId,
                                    String decidedByName, String decision,
                                    String decisionNote, LocalDateTime decidedAt) {
}
