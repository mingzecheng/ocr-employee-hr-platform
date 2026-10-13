package com.hrplatform.contract;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ContractReminderMapper {

    /**
     * 找出 {@code renewal_status='NOT_DUE'} 且到期日在 {@code [today, through]} 内的合同。
     * 调度器用它一次性取出 30 天窗口内的所有候选，再按 30/7/1 分窗口决定生成哪些事件。
     */
    List<DueContractRow> findDueContractsWithin(@Param("today") LocalDate today,
                                                @Param("through") LocalDate through);

    /** 该合同在该阶段是否已经生成过提醒事件（幂等判断）。 */
    boolean exists(@Param("contractId") Long contractId, @Param("stage") String stage);

    /** 插入一条 PENDING 提醒事件。受 uk_contract_stage 保护，重复插入由调用方先用 exists 拦截。 */
    int insert(@Param("contractId") Long contractId,
               @Param("dueDate") LocalDate dueDate,
               @Param("stage") String stage);

    ContractReminderEvent findById(@Param("id") Long id);

    /** 仅对仍为 PENDING 的事件置 ACKED，返回受影响行数（已 ACKED 或不存在时为 0）。 */
    int ack(@Param("id") Long id, @Param("ackedAt") LocalDateTime ackedAt);

    /**
     * 待办聚合用：尚未 ACKED、且合同仍处于 NOT_DUE（尚未做出续签决定）的提醒事件。
     * 按员工部门做 DataScope 过滤，HR(ALL) 全见、部门负责人见本部门。
     */
    List<ContractReminderTodoRow> findPendingForTodo(@Param("scopeType") String scopeType,
                                                     @Param("employeeId") Long employeeId,
                                                     @Param("departmentId") Long departmentId,
                                                     @Param("limit") int limit);

    /** 调度器候选行：合同 id 与到期日。 */
    record DueContractRow(Long contractId, LocalDate dueDate) {
    }

    /** 待办聚合行：足够渲染一条待办，以及按到期日算优先级。 */
    record ContractReminderTodoRow(Long id, String title, Long resourceId, Long employeeId,
                                  String status, String stage, LocalDateTime dueAt,
                                  LocalDateTime createdAt) {
    }
}
