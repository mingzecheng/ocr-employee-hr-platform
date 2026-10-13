package com.hrplatform.workflow;

import com.hrplatform.audit.OperationLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class EmployeeChangeScheduler {
    private static final Logger log = LoggerFactory.getLogger(EmployeeChangeScheduler.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final RequestMapper mapper;
    private final RequestService requestService;
    private final OperationLogService operationLogService;
    private final EmployeeChangeService employeeChangeService;

    public EmployeeChangeScheduler(RequestMapper mapper, RequestService requestService,
                                   OperationLogService operationLogService,
                                   EmployeeChangeService employeeChangeService) {
        this.mapper = mapper;
        this.requestService = requestService;
        this.operationLogService = operationLogService;
        this.employeeChangeService = employeeChangeService;
    }

    @Scheduled(cron = "${hr.workflow.employee-change-cron:0 * * * * *}", zone = "Asia/Shanghai")
    public void applyDueEmployeeChanges() {
        for (Long requestId : mapper.listDueEmployeeChangeIds(LocalDate.now(BUSINESS_ZONE), 100)) {
            try {
                requestService.applyDueEmployeeChange(requestId);
            } catch (RuntimeException exception) {
                log.error("Failed to apply approved employee change request {}", requestId, exception);
                try {
                    operationLogService.record(null, "APPLY_EMPLOYEE_CHANGE", "HR_REQUEST", requestId, "FAILED");
                } catch (RuntimeException auditException) {
                    log.error("Failed to audit employee change failure for request {}", requestId, auditException);
                }
            }
        }
    }

    /**
     * 每日（默认 05:05）扫描已批准请假单，按生效窗口把员工在 ACTIVE/ON_LEAVE 之间切换。
     * 幂等：EmployeeChangeService.syncLeaveStatus 会比对当前状态，重复执行不重复写历史。
     */
    @Scheduled(cron = "${hr.workflow.leave-status-cron:0 5 * * * ?}", zone = "Asia/Shanghai")
    public void applyLeaveStatusTransitions() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        for (LeaveStatusWindow window : mapper.listLeaveStatusWindowsForSync(100)) {
            try {
                employeeChangeService.syncLeaveStatus(window, today);
            } catch (RuntimeException exception) {
                log.error("Failed to synchronize leave status for leave request {}", window.requestId(), exception);
            }
        }
    }
}
