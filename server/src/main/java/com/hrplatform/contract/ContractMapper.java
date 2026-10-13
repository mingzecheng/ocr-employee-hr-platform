package com.hrplatform.contract;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDate;

@Mapper
public interface ContractMapper {
    int insert(ContractRecord contract);

    ContractRecord findByContractNo(@Param("contractNo") String contractNo);

    ContractRecord findByIdWithScope(@Param("id") Long id,
                                     @Param("employeeId") Long employeeId,
                                     @Param("departmentId") Long departmentId,
                                     @Param("scopeType") String scopeType);

    List<ContractRecord> list(@Param("employeeId") Long employeeId,
                              @Param("status") String status,
                              @Param("renewalStatus") String renewalStatus,
                              @Param("scopeEmployeeId") Long scopeEmployeeId,
                              @Param("departmentId") Long departmentId,
                              @Param("scopeType") String scopeType,
                              @Param("dueFrom") LocalDate dueFrom,
                              @Param("dueThrough") LocalDate dueThrough,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    long count(@Param("employeeId") Long employeeId,
               @Param("status") String status,
               @Param("renewalStatus") String renewalStatus,
               @Param("scopeEmployeeId") Long scopeEmployeeId,
               @Param("departmentId") Long departmentId,
               @Param("scopeType") String scopeType,
               @Param("dueFrom") LocalDate dueFrom,
               @Param("dueThrough") LocalDate dueThrough);

    int markRenewed(@Param("id") Long id, @Param("versionNo") int versionNo,
                    @Param("operatorId") Long operatorId);

    int markNotRenewing(@Param("id") Long id, @Param("versionNo") int versionNo,
                        @Param("operatorId") Long operatorId);
}
