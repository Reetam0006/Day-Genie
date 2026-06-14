package com.daygenie.controller;

import com.daygenie.model.*;
import com.daygenie.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final UserService userService;

    private Long userId(UserDetails ud) {
        return userService.findByUsername(ud.getUsername()).getId();
    }

    /** GET /api/tasks — all PENDING/IN_PROGRESS tasks */
    @GetMapping
    public ResponseEntity<List<Task>> getAll(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(taskService.getActiveTasks(userId(ud)));
    }

    /** GET /api/tasks/today */
    @GetMapping("/today")
    public ResponseEntity<List<Task>> getToday(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(taskService.getTasksForToday(userId(ud)));
    }

    /** GET /api/tasks/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Task> getOne(@PathVariable Long id,
                                       @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(taskService.getTask(id, userId(ud)));
    }

    /** POST /api/tasks — structured task creation */
    @PostMapping
    public ResponseEntity<Task> create(@RequestBody Task task,
                                       @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(taskService.createTask(task, userId(ud)));
    }

    /** POST /api/tasks/nlp — natural language task creation */
    @PostMapping("/nlp")
    public ResponseEntity<?> createFromNlp(@RequestBody Map<String, String> body,
                                           @AuthenticationPrincipal UserDetails ud) {
        String raw = body.get("rawInput");
        if (raw == null || raw.isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "rawInput is required"));
        Task task = taskService.createTaskFromNlp(raw, userId(ud));
        return ResponseEntity.ok(task);
    }

    /** PUT /api/tasks/{id} — update task fields */
    @PutMapping("/{id}")
    public ResponseEntity<Task> update(@PathVariable Long id,
                                       @RequestBody Task updates,
                                       @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(taskService.updateTask(id, updates, userId(ud)));
    }

    /** PATCH /api/tasks/{id}/complete — mark task as done */
    @PatchMapping("/{id}/complete")
    public ResponseEntity<Map<String, String>> complete(@PathVariable Long id,
                                                        @AuthenticationPrincipal UserDetails ud) {
        taskService.completeTask(id, userId(ud));
        return ResponseEntity.ok(Map.of("message", "Task completed"));
    }

    /** DELETE /api/tasks/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id,
                                                      @AuthenticationPrincipal UserDetails ud) {
        taskService.deleteTask(id, userId(ud));
        return ResponseEntity.ok(Map.of("message", "Task deleted"));
    }

    /** POST /api/tasks/{id}/refresh — re-run context enrichment */
    @PostMapping("/{id}/refresh")
    public ResponseEntity<Task> refresh(@PathVariable Long id,
                                        @AuthenticationPrincipal UserDetails ud) {
        Task task = taskService.getTask(id, userId(ud));
        taskService.enrichWithContext(task);
        return ResponseEntity.ok(taskService.updateTask(id, task, userId(ud)));
    }
}