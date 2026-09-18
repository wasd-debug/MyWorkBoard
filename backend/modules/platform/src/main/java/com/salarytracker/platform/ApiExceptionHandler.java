package com.salarytracker.platform;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiProblem> conflict(ConflictException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "REVISION_CONFLICT", exception.getMessage(), request,
                exception.getServerRevision());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiProblem> badRequest(IllegalArgumentException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), request, null);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiProblem> unauthorized(UnauthorizedException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", exception.getMessage(), request, null);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiProblem> forbidden(ForbiddenException exception, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "LEDGER_FORBIDDEN", exception.getMessage(), request, null);
    }

    @ExceptionHandler(SyncResetRequiredException.class)
    public ResponseEntity<ApiProblem> syncReset(SyncResetRequiredException exception, HttpServletRequest request) {
        return problem(HttpStatus.GONE, "SYNC_RESET_REQUIRED", exception.getMessage(), request, null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            MissingRequestHeaderException.class})
    public ResponseEntity<ApiProblem> invalidRequest(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求内容或参数无效", request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiProblem> notFound(NoResourceFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "请求的资源不存在", request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiProblem> methodNotAllowed(HttpRequestMethodNotSupportedException exception,
                                                       HttpServletRequest request) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "请求方法不受支持", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiProblem> internal(Exception exception, HttpServletRequest request) {
        log.error("Unhandled API exception trace_id={} method={} uri={}",
                TraceIds.current(), request.getMethod(), request.getRequestURI(), exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务暂时不可用", request, null);
    }

    private ResponseEntity<ApiProblem> problem(HttpStatus status, String code, String detail,
                                               HttpServletRequest request, Long serverRevision) {
        ApiProblem result = ApiProblem.of(status, code, detail, request, serverRevision);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(result);
    }
}
