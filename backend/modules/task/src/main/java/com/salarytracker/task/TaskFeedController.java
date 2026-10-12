package com.salarytracker.task;

import com.salarytracker.platform.ApiResponse;
import com.salarytracker.task.TaskModels.FeedToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/tasks/feed")
@Tag(name = "Task Feed")
public class TaskFeedController {
    private static final MediaType CALENDAR = MediaType.parseMediaType("text/calendar;charset=UTF-8");
    private final TaskFeedService feeds;

    public TaskFeedController(TaskFeedService feeds) { this.feeds = feeds; }

    @GetMapping("/token")
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "getTaskFeedToken")
    public ApiResponse<FeedToken> status() { return ApiResponse.ok(feeds.status()); }

    @PostMapping("/token")
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "createTaskFeedToken")
    public ApiResponse<FeedToken> create() { return ApiResponse.ok(feeds.create(baseUrl())); }

    @PutMapping("/token")
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "rotateTaskFeedToken")
    public ApiResponse<FeedToken> rotate() { return ApiResponse.ok(feeds.rotate(baseUrl())); }

    @DeleteMapping("/token")
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "revokeTaskFeedToken")
    public ApiResponse<FeedToken> revoke() { return ApiResponse.ok(feeds.revoke()); }

    @GetMapping(value = "/{token}.ics", produces = "text/calendar;charset=UTF-8")
    @Operation(operationId = "readTaskFeed")
    public ResponseEntity<String> feed(@PathVariable String token, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(CALENDAR)
                .body(feeds.feed(token, request.getRemoteAddr()));
    }

    private String baseUrl() { return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString(); }
}
