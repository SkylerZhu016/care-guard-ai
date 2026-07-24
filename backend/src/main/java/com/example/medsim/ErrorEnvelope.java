package com.example.medsim;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

record FieldErrorView(String field, String code) {}
record ErrorBody(OffsetDateTime timestamp, String requestId, String code, String message, List<FieldErrorView> fieldErrors) {}

class ApiException extends RuntimeException {
    final HttpStatus status; final String code;
    ApiException(HttpStatus status, String code, String message) { super(message); this.status = status; this.code = code; }
    static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "资源不存在或无权访问"); }
    static ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "无权执行该操作"); }
    static ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
}

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> api(ApiException ex) { return ResponseEntity.status(ex.status).body(body(ex.code, ex.getMessage(), List.of())); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException ex) {
        var fields = ex.getBindingResult().getFieldErrors().stream().map(e -> new FieldErrorView(e.getField(), e.getCode())).toList();
        return ResponseEntity.badRequest().body(body("COMMON_VALIDATION_FAILED", "输入校验失败", fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorBody> malformed(Exception ex) {
        return ResponseEntity.badRequest().body(body("COMMON_VALIDATION_FAILED", "输入格式或枚举值无效", List.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorBody> denied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body("AUTH_FORBIDDEN", "无权执行该操作", List.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> fallback(Exception ex) {
        return ResponseEntity.internalServerError().body(body("INTERNAL_ERROR", "系统暂时不可用，请稍后重试", List.of()));
    }

    private ErrorBody body(String code, String message, List<FieldErrorView> fields) {
        return new ErrorBody(OffsetDateTime.now(), UUID.randomUUID().toString(), code, message, fields);
    }
}

final class ErrorEnvelope {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private ErrorEnvelope() {}
    static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        MAPPER.writeValue(response.getWriter(), new ErrorBody(OffsetDateTime.now(), UUID.randomUUID().toString(), code, message, List.of()));
    }
}
