package com.hrplatform.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import org.slf4j.MDC;
import com.hrplatform.common.web.TraceIdFilter;

@Service
public class OperationLogService {
    private static final Logger log = LoggerFactory.getLogger(OperationLogService.class);
    private final OperationLogMapper mapper;

    @Autowired
    public OperationLogService(OperationLogMapper mapper) {
        this.mapper = mapper;
    }

    public void record(Long operatorId, String action, String objectType, Long objectId, String result) {
        log.info("operation operatorId={} action={} objectType={} objectId={} result={}",
                operatorId, action, objectType, objectId, result);
        String traceId = MDC.get(TraceIdFilter.HEADER);
        mapper.insert(new OperationLogRecord(null, operatorId, action, objectType, objectId,
                result, traceId == null ? "unknown" : traceId, LocalDateTime.now()));
    }
}
