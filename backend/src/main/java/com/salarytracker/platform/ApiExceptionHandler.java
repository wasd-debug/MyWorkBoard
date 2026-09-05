package com.salarytracker.platform;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ConflictException.class)
    public ProblemDetail conflict(ConflictException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "REVISION_CONFLICT", exception.getMessage(), request,
                Map.of("serverRevision", exception.getServerRevision()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail unauthorized(UnauthorizedException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail internal(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务暂时不可用", request, Map.of());
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail,
                                  HttpServletRequest request, Map<String, Object> extras) {
        ProblemDetail result = ProblemDetail.forStatusAndDetail(status, detail == null ? status.getReasonPhrase() : detail);
        result.setTitle(code);
        result.setProperty("code", code);
        result.setProperty("trace_id", TraceIds.current());
        result.setProperty("path", request.getRequestURI());
        result.setProperty("timestamp", Instant.now().toString());
        extras.forEach(result::setProperty);
        return result;
    }
}
