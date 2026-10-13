package com.hrplatform.workflow;

import java.util.List;

public record RequestDetail(HrRequest request, RequestDisplayContext display,
                            List<ApprovalRecord> approvals) {
    public RequestDetail(HrRequest request, List<ApprovalRecord> approvals) {
        this(request, null, approvals);
    }

    public RequestDetail {
        approvals = approvals == null ? List.of() : List.copyOf(approvals);
    }
}
