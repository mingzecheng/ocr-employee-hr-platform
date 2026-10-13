package com.hrplatform.audit;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface OperationLogMapper {
    int insert(OperationLogRecord record);
    List<OperationLogRecord> list(@Param("operatorId") Long operatorId, @Param("action") String action,
                                  @Param("objectType") String objectType, @Param("result") String result,
                                  @Param("fromInclusive") LocalDateTime fromInclusive,
                                  @Param("toExclusive") LocalDateTime toExclusive,
                                  @Param("offset") int offset, @Param("limit") int limit);
    long count(@Param("operatorId") Long operatorId, @Param("action") String action,
               @Param("objectType") String objectType, @Param("result") String result,
               @Param("fromInclusive") LocalDateTime fromInclusive,
               @Param("toExclusive") LocalDateTime toExclusive);
    OperationLogRecord findById(@Param("id") Long id);

    /** 登录相关日志（LOGIN / LOGOUT / REFRESH）分页，按时间倒序。 */
    List<OperationLogRecord> listLoginLogs(@Param("offset") int offset, @Param("limit") int limit);

    long countLoginLogs();
}
