package com.hrplatform.statistics;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface StatisticsMapper {
    StatisticsRaw aggregate(@Param("scopeType") String scopeType, @Param("employeeId") Long employeeId,
                            @Param("departmentId") Long departmentId, @Param("from") LocalDate from,
                            @Param("to") LocalDate to);
}
