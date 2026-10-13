package com.hrplatform.access;

import java.util.List;

public record ArchiveAccessDetail(ArchiveAccessApplication application, String applicantName,
                                  String departmentName, List<ArchiveAccessDetailItem> items,
                                  List<ArchiveUseDetail> uses,
                                  List<ArchiveAccessResolution> resolutions,
                                  List<ArchiveAccessDecision> decisions) {
    public ArchiveAccessDetail {
        items = items == null ? List.of() : List.copyOf(items);
        uses = uses == null ? List.of() : List.copyOf(uses);
        resolutions = resolutions == null ? List.of() : List.copyOf(resolutions);
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }
}
