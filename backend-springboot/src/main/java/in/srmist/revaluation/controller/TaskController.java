package in.srmist.revaluation.controller;

import in.srmist.revaluation.dto.TaskRequests;
import in.srmist.revaluation.dto.Views.Message;
import in.srmist.revaluation.dto.Views.TaskDetail;
import in.srmist.revaluation.dto.Views.TaskView;
import in.srmist.revaluation.service.AuthService;
import in.srmist.revaluation.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Staff task inbox (replaces Camunda Tasklist in the demo). Every call needs a signed-in user:
 * the CoE sees the CoE tasks, an examiner sees only the scripts assigned to them.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService service;
    private final AuthService auth;

    public TaskController(TaskService service, AuthService auth) {
        this.service = service;
        this.auth = auth;
    }

    @GetMapping
    public List<TaskView> list(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return service.list(auth.require(authorization));
    }

    @GetMapping("/{taskKey}")
    public TaskDetail get(@PathVariable long taskKey,
                          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return service.get(taskKey, auth.require(authorization));
    }

    @PostMapping("/{taskKey}/complete")
    public Message complete(@PathVariable long taskKey, @Valid @RequestBody TaskRequests.Complete body,
                            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        service.complete(taskKey, body, auth.require(authorization));
        return new Message("Task completed");
    }
}
