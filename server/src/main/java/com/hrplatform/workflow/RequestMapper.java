package com.hrplatform.workflow;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RequestMapper {
    int insert(HrRequest request);

    int insertLeaveDetail(LeaveRequestDetail detail);

    int insertOnboardingDetail(OnboardingDetail detail);

    int insertTransferDetail(TransferDetail detail);

    TransferDetail findTransferDetail(@Param("requestId") Long requestId);

    long countOpenEmployeeChanges(@Param("employeeId") Long employeeId,
                                  @Param("excludeRequestId") Long excludeRequestId);

    boolean hasEarlierUnappliedEmployeeChange(@Param("employeeId") Long employeeId,
                                               @Param("effectiveDate") java.time.LocalDate effectiveDate,
                                               @Param("requestId") Long requestId);

    int insertOffboardingDetail(OffboardingDetail detail);

    boolean isHandoverComplete(@Param("requestId") Long requestId);

    int completeHandover(@Param("requestId") Long requestId);

    HrRequest findByRequestNo(@Param("requestNo") String requestNo);

    HrRequest findByIdWithScope(@Param("id") Long id, @Param("employeeId") Long employeeId,
                                @Param("departmentId") Long departmentId,
                                @Param("scopeType") String scopeType);

    int updateState(HrRequest request);

    int insertApproval(ApprovalRecord approval);

    java.util.List<ApprovalRecord> listApprovals(@Param("requestId") Long requestId);

    RequestDisplayContext findDisplayContext(@Param("requestId") Long requestId);

    java.util.List<Long> listDueEmployeeChangeIds(@Param("effectiveOnOrBefore") java.time.LocalDate effectiveOnOrBefore,
                                                  @Param("limit") int limit);

    Long lockDueEmployeeChange(@Param("id") Long id,
                               @Param("effectiveOnOrBefore") java.time.LocalDate effectiveOnOrBefore);

    int markEmployeeChangeApplied(@Param("id") Long id, @Param("appliedAt") java.time.LocalDateTime appliedAt);

    /**
     * 请假审批通过时写入自动状态切换窗口（幂等：已有窗口则跳过，不覆盖）。
     */
    int markLeaveEffectiveWindow(@Param("requestId") Long requestId,
                                 @Param("effectiveFrom") java.time.LocalDate effectiveFrom,
                                 @Param("effectiveTo") java.time.LocalDate effectiveTo);

    /**
     * 调度器拉取所有已批准且已写入窗口的请假单，用于每日状态切换。
     */
    java.util.List<LeaveStatusWindow> listLeaveStatusWindowsForSync(@Param("limit") int limit);

    /**
     * 销假回 ACTIVE 前的守卫：统计该员工其他已批准请假单中，窗口仍覆盖今天的数量。
     * 大于 0 说明还有重叠请假在生效，不应切回 ACTIVE。
     */
    long countOtherActiveLeaveWindows(@Param("employeeId") Long employeeId,
                                      @Param("today") java.time.LocalDate today,
                                      @Param("excludeRequestId") Long excludeRequestId);

    java.util.List<HrRequest> list(@Param("requestType") String requestType,
                                   @Param("status") String status,
                                   @Param("employeeId") Long employeeId,
                                   @Param("departmentId") Long departmentId,
                                   @Param("scopeType") String scopeType,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    long count(@Param("requestType") String requestType,
               @Param("status") String status,
               @Param("employeeId") Long employeeId,
               @Param("departmentId") Long departmentId,
               @Param("scopeType") String scopeType);
}
