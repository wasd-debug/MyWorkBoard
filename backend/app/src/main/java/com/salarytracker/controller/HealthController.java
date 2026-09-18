package com.salarytracker.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Operations")
public class HealthController {
    @GetMapping("/api/health")
    @Operation(operationId = "getHealth", summary = "Read service health")
    public HealthResponse health() {
        return new HealthResponse(true);
    }

    public record HealthResponse(boolean ok) {
    }
}
