package com.silvershuo.codeagent.interfaces.rest;

import com.silvershuo.codeagent.application.command.CreateTaskCommand;
import com.silvershuo.codeagent.application.command.ResumeTaskCommand;
import com.silvershuo.codeagent.application.service.AgentApplicationService;
import com.silvershuo.codeagent.interfaces.dto.EventLogResponse;
import com.silvershuo.codeagent.interfaces.dto.TaskCreateRequest;
import com.silvershuo.codeagent.interfaces.dto.TaskResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final AgentApplicationService applicationService;

    public TaskController(AgentApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public TaskResponse createTask(@Valid @RequestBody TaskCreateRequest request) {
        return applicationService.createTaskAndRun(new CreateTaskCommand(request.getTitle(), request.getPrompt(), request.getWorkingDirectory()));
    }

    @PostMapping("/{taskId}/resume")
    public TaskResponse resumeTask(@PathVariable Long taskId) {
        return applicationService.resumeTask(new ResumeTaskCommand(taskId));
    }

    @GetMapping("/{taskId}")
    public TaskResponse getTask(@PathVariable Long taskId) {
        return applicationService.getTask(taskId);
    }

    @GetMapping("/{taskId}/events")
    public List<EventLogResponse> getTaskEvents(@PathVariable Long taskId) {
        return applicationService.getTaskEvents(taskId);
    }
}
