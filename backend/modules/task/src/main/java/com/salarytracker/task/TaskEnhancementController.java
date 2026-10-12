package com.salarytracker.task;

import com.salarytracker.platform.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.salarytracker.task.TaskEnhancementModels.*;

@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Task enhancements")
public class TaskEnhancementController {
    private final TaskEnhancementService service;
    public TaskEnhancementController(TaskEnhancementService service) { this.service = service; }

    @GetMapping("/filters") @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "listTaskFilters")
    public ApiResponse<List<SmartFilter>> filters() { return ApiResponse.ok(service.filters()); }

    @PostMapping(value="/filters", consumes=MediaType.APPLICATION_JSON_VALUE) @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "createTaskFilter")
    public ApiResponse<SmartFilter> createFilter(@RequestBody SmartFilterCommand body) { return ApiResponse.ok(service.createFilter(body)); }

    @PutMapping(value="/filters/{id}", consumes=MediaType.APPLICATION_JSON_VALUE) @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "updateTaskFilter")
    public ApiResponse<SmartFilter> updateFilter(@PathVariable String id, @RequestBody SmartFilterCommand body,
                                                  @RequestHeader("If-Match") long revision) { return ApiResponse.ok(service.updateFilter(id,body,revision)); }

    @DeleteMapping("/filters/{id}") @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "deleteTaskFilter")
    public ApiResponse<Boolean> deleteFilter(@PathVariable String id,@RequestHeader("If-Match") long revision) { return ApiResponse.ok(service.deleteFilter(id,revision)); }

    @GetMapping("/filters/{id}/tasks") @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "listTaskFilterTasks")
    public ApiResponse<TaskModels.TaskPage> filterTasks(@PathVariable String id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size){return ApiResponse.ok(service.filterTasks(id,page,size));}

    @GetMapping("/board") @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "getTaskBoard")
    public ApiResponse<Board> board(@RequestParam(defaultValue="list")String dimension){return ApiResponse.ok(service.board(dimension));}

    @GetMapping("/quadrant") @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "getTaskQuadrant")
    public ApiResponse<Board> quadrant(){return ApiResponse.ok(service.quadrant());}

    @GetMapping("/search") @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "searchTasks")
    public ApiResponse<SearchResult> search(@RequestParam String q){return ApiResponse.ok(service.search(q));}

    @GetMapping("/stats") @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "getTaskStats")
    public ApiResponse<Stats> stats(@RequestParam(required=false)String from,@RequestParam(required=false)String to){return ApiResponse.ok(service.stats(from,to));}

    @PostMapping(value="/parse",consumes=MediaType.APPLICATION_JSON_VALUE) @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "parseTaskQuickAdd")
    public ApiResponse<ParsedDraft> parse(@RequestBody ParseCommand body){return ApiResponse.ok(service.parse(body));}

    @PostMapping(value="/import/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "previewTaskImport")
    public ApiResponse<ImportPreview> preview(@RequestPart("file")MultipartFile file)throws Exception{return ApiResponse.ok(service.preview(file));}

    @PostMapping(value="/import/commit",consumes=MediaType.APPLICATION_JSON_VALUE) @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "commitTaskImport")
    public ApiResponse<ImportResult> commit(@RequestBody ImportCommitCommand body){return ApiResponse.ok(service.commit(body));}

    @PostMapping(value="/export",consumes=MediaType.APPLICATION_JSON_VALUE) @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "exportTasks")
    public ResponseEntity<ByteArrayResource> export(@RequestBody ExportCommand body){
        String format=body==null||body.format()==null?"csv":body.format().toLowerCase(); byte[]data=service.export(body);
        String extension="xlsx".equals(format)?"xlsx":"ics".equals(format)?"ics":"csv";
        MediaType type="xlsx".equals(extension)?MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                :"ics".equals(extension)?MediaType.parseMediaType("text/calendar;charset=UTF-8"):MediaType.parseMediaType("text/csv;charset=UTF-8");
        return ResponseEntity.ok().contentType(type).contentLength(data.length).header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("tasks."+extension, StandardCharsets.UTF_8).build().toString()).body(new ByteArrayResource(data));
    }
}
