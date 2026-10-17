package com.saasno.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "log")
public class LogEntity {

    @Id
    private String id;

    @Field("user_id")
    private String userId;

    @Field("username")
    private String username;

    @Indexed
    @Field("trace_id")
    private String traceId;

    @Field("method")
    private String method;

    @Field("uri")
    private String uri;

    @Field("client_ip")
    private String clientIp;

    @Field("status_code")
    private int statusCode;

    @Field("execution_time_ms")
    private long executionTimeMs;

    @Field("handler_method")
    private String handlerMethod;

    @Field("error_message")
    private String errorMessage;

    @Field("request_param")
    private String requestParam;

    @Field("request_body")
    private String requestBody;

    @Field("response_body")
    private String responseBody;

    @Indexed
    @Field("created_at")
    private LocalDateTime registered;

    public LogEntity() {
    }

    public LogEntity(String userId, String username, String traceId, String method, String uri, String clientIp,
            int statusCode, long executionTimeMs, String handlerMethod,
            String errorMessage, LocalDateTime registered) {

        this.userId = userId;
        this.username = username;
        this.traceId = traceId;
        this.method = method;
        this.uri = uri;
        this.clientIp = clientIp;
        this.statusCode = statusCode;
        this.executionTimeMs = executionTimeMs;
        this.handlerMethod = handlerMethod;
        this.errorMessage = errorMessage;
        this.registered = registered;
    }

    // 3. Getter dan Setter Manual
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public String getHandlerMethod() {
        return handlerMethod;
    }

    public void setHandlerMethod(String handlerMethod) {
        this.handlerMethod = handlerMethod;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getRequestParam() {
        return requestParam;
    }

    public void setRequestParam(String requestParam) {
        this.requestParam = requestParam;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public LocalDateTime getRegistered() {
        return registered;
    }

    public void setRegistered(LocalDateTime registered) {
        this.registered = registered;
    }

    @Override
    public String toString() {
        return "LogEntity{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", traceId='" + traceId + '\'' +
                ", method='" + method + '\'' +
                ", uri='" + uri + '\'' +
                ", clientIp='" + clientIp + '\'' +
                ", statusCode=" + statusCode +
                ", executionTimeMs=" + executionTimeMs +
                ", handlerMethod='" + handlerMethod + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", registered=" + registered +
                '}';
    }
}
