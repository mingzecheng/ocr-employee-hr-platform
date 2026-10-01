package com.hrplatform.common.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PrimaryDataSourceAspect {

    @Around("@annotation(com.hrplatform.common.datasource.UsePrimary)"
            + " || @within(com.hrplatform.common.datasource.UsePrimary)")
    public Object routeToPrimary(ProceedingJoinPoint joinPoint) throws Throwable {
        ReadWriteRoutingDataSource.pushPrimary();
        try {
            return joinPoint.proceed();
        } finally {
            ReadWriteRoutingDataSource.popPrimary();
        }
    }
}
