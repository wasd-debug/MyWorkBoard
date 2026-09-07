package com.salarytracker.ai;

import com.salarytracker.ledger.LedgerService;
import com.salarytracker.platform.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {
    private final LlmGateway gateway;
    private final LedgerService ledger;

    public AiController(LlmGateway gateway, LedgerService ledger) { this.gateway = gateway; this.ledger = ledger; }

    @PostMapping("/chat")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> body) { return ApiResponse.ok(gateway.chat(String.valueOf(body.getOrDefault("message", "")))); }

    @PostMapping("/ledger/preview")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> ledgerPreview(@RequestBody Map<String, Object> body) { return ApiResponse.ok(ledger.aiPreview(String.valueOf(body.getOrDefault("text", "")))); }
}
