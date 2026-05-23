package com.tripmate.backend.common.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class ExecutionTimeAspect {

    @Around("@within(com.tripmate.backend.common.aop.TrackExecutionTime) || @annotation(com.tripmate.backend.common.aop.TrackExecutionTime)")
    public Object measureExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startNanos = System.nanoTime();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String method = signature.getDeclaringType().getSimpleName() + "." + signature.getName();

        try {
            Object result = joinPoint.proceed();
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("[AOP-TIMER] {} completed in {} ms", method, elapsedMillis);
            return result;
        } catch (Throwable throwable) {
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
            log.warn("[AOP-TIMER] {} failed after {} ms: {}", method, elapsedMillis, throwable.getClass().getSimpleName());
            throw throwable;
        }
    }
}