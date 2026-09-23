package com.salarytracker.ai.model;

import com.salarytracker.platform.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value="/api/v1/agent/model-connections",produces=MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("isAuthenticated()")
@Tag(name="Agent Model Connections")
public class AiModelConnectionController {
    private final AiModelConnectionService service;
    public AiModelConnectionController(AiModelConnectionService service){this.service=service;}
    @GetMapping @Operation(operationId="listAgentModelConnections") public ApiResponse<List<AiModelConnectionService.ConnectionView>> list(){return ApiResponse.ok(service.list());}
    @GetMapping("/{id}") @Operation(operationId="getAgentModelConnection") public ApiResponse<AiModelConnectionService.ConnectionView> get(@PathVariable String id){return ApiResponse.ok(service.get(id));}
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE) @Operation(operationId="createAgentModelConnection") public ApiResponse<AiModelConnectionService.ConnectionView> create(@RequestBody AiModelConnectionService.ConnectionCommand command){return ApiResponse.ok(service.create(command));}
    @PutMapping(value="/{id}",consumes=MediaType.APPLICATION_JSON_VALUE) @Operation(operationId="updateAgentModelConnection") public ApiResponse<AiModelConnectionService.ConnectionView> update(@PathVariable String id,@RequestBody AiModelConnectionService.ConnectionCommand command){return ApiResponse.ok(service.update(id,command));}
    @DeleteMapping("/{id}") @Operation(operationId="deleteAgentModelConnection") public ApiResponse<Map<String,Boolean>> delete(@PathVariable String id){service.delete(id);return ApiResponse.ok(Map.of("deleted",true));}
    @PostMapping("/{id}/test") @Operation(operationId="testAgentModelConnection") public ApiResponse<AiModelConnectionService.TestResult> test(@PathVariable String id){return ApiResponse.ok(service.test(id));}
    @PostMapping("/{id}/set-default") @Operation(operationId="setDefaultAgentModelConnection") public ApiResponse<AiModelConnectionService.ConnectionView> setDefault(@PathVariable String id){return ApiResponse.ok(service.setDefault(id));}
}
