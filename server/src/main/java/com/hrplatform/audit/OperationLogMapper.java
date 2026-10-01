package com.hrplatform.audit;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDate;

@Mapper
public interface OperationLogMapper {
    int insert(OperationLogRecord record);
    List<OperationLogRecord> list(@Param("operatorId") Long operatorId, @Param("action") String action,
                                  @Param("objectType") String objectType, @Param("result") String result,
                                  @Param("from") LocalDate from, @Param("to") LocalDate to,
                                  @Param("offset") int offset, @Param("limit") int limit);
    long count(@Param("operatorId") Long operatorId, @Param("action") String action,
               @Param("objectType") String objectType, @Param("result") String result,
               @Param("from") LocalDate from, @Param("to") LocalDate to);
}
