package com.hrplatform.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OperationLogService {
    private static final Logger log = LoggerFactory.getLogger(OperationLogService.class);

    public void record(Long operatorId, String action, String objectType, Long objectId, String result) {
        log.info("operation operatorId={} action={} objectType={} objectId={} result={}",
                operatorId, action, objectType, objectId, result);
    }
}
