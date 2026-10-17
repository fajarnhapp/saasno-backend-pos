package com.saasno.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.WebUtils;

import com.saasno.entity.LogEntity;
import com.saasno.entity.SessionEntity;
import com.saasno.utility.ConverterUtility;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.logger.Log;
import com.saasno.utility.logger.LogMasker;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.wrapper.MultipleReadRequestWrapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Aspect
@Component
public class ApiLoggingAspect {

    private final LogService logService;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final SessionUtility sessionUtility;

    public ApiLoggingAspect(
            LogService logService,
            HttpServletRequest request,
            HttpServletResponse response,
            SessionUtility sessionUtility) {

        this.logService = logService;
        this.request = request;
        this.response = response;
        this.sessionUtility = sessionUtility;
    }

    @Around("within(@org.springframework.web.bind.annotation.RestController *) || " +
            "within(@org.springframework.stereotype.Controller *)")
    public Object logApiAccess(ProceedingJoinPoint joinPoint) throws Throwable {

        long startTime = System.currentTimeMillis();

        String userId = sessionUtility.getUserId();
        String username = sessionUtility.getUsername();

        if (userId == null) {
            userId = "ANONYMOUS";
        }

        if (username == null) {
            username = "ANONYMOUS";
        }

        String traceId = UUID.randomUUID().toString();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String clientIp = getClientIp(request);

        String handlerMethod = joinPoint.getSignature().getDeclaringTypeName()
                + "."
                + joinPoint.getSignature().getName();

        int statusCode = 200;
        String errorMessage = null;
        Object result = null;

        try {

            result = joinPoint.proceed();

            statusCode = response.getStatus();

            return result;

        } catch (Throwable ex) {

            statusCode = response.getStatus();

            if (statusCode <= 0) {
                statusCode = 500;
            }

            errorMessage = ex.getMessage();

            throw ex;

        } finally {
            if (!"GET".equalsIgnoreCase(method)) {
                try {

                    long executionTime = System.currentTimeMillis() - startTime;

                    LogEntity log = new LogEntity();

                    log.setTraceId(traceId);
                    log.setMethod(method);
                    log.setUri(uri);
                    log.setClientIp(clientIp);
                    log.setStatusCode(statusCode);
                    log.setExecutionTimeMs(executionTime);
                    log.setHandlerMethod(handlerMethod);
                    log.setErrorMessage(errorMessage);
                    log.setRequestParam(LogMasker.queryString(request.getQueryString()));
                    MultipleReadRequestWrapper wrapper = WebUtils.getNativeRequest(
                            ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest(),
                            MultipleReadRequestWrapper.class);
                    if (wrapper != null) {
                        log.setRequestBody(LogMasker.body(wrapper.getBody()));
                    }
                    log.setResponseBody(toResponseBody(result));
                    log.setUserId(userId);
                    log.setUsername(username);
                    log.setRegistered(LocalDateTime.now());

                    logService.save(log);

                } catch (Exception e) {
                    Log.error("Failed save audit log : " + e.getMessage());
                }
            }
        }
    }

    private String toResponseBody(Object result) {
        Object body = result instanceof ResponseEntity<?> entity ? entity.getBody() : result;
        if (body == null) {
            return null;
        }
        // Session id doubles as the bearer token, so login/OTP responses must never be persisted.
        if (body instanceof ResponseModel<?> model && model.getData() instanceof SessionEntity) {
            return "[Session data hidden]";
        }
        return LogMasker.body(ConverterUtility.toString(body));
    }

    private String getClientIp(HttpServletRequest request) {

        String xForwardedFor = request.getHeader("X-Forwarded-For");

        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");

        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }

        return request.getRemoteAddr();
    }
}