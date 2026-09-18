package com.salarytracker.platform;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.time.Instant;

@Schema(name = "ApiProblem", description = "RFC 7807 API error with stable application metadata")
public record ApiProblem(
        URI type,
        String title,
        int status,
        String detail,
        URI instance,
        String code,
        String traceId,
        String path,
        Instant timestamp,
        @Schema(nullable = true, description = "Current server revision for revision conflicts") Long serverRevision) {

    public static ApiProblem of(HttpStatus status, String code, String detail,
                                HttpServletRequest request, Long serverRevision) {
        String path = request.getRequestURI();
        return new ApiProblem(
                URI.create("about:blank"),
                code,
                status.value(),
                detail == null ? status.getReasonPhrase() : detail,
                URI.create(path),
                code,
                TraceIds.current(),
                path,
                Instant.now(),
                serverRevision);
    }
}
