package com.silvershuo.codeagent.domain.task.model;

import java.time.LocalDateTime;

public class AgentTask {

    private Long id;
    private String title;
    private String userPrompt;
    private String workingDirectory;
    private TaskStatus status;
    private String modelName;
    private String finalResult;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AgentTask(Long id, String title, String userPrompt, String workingDirectory, String modelName) {
        this.id = id;
        this.title = title;
        this.userPrompt = userPrompt;
        this.workingDirectory = workingDirectory;
        this.modelName = modelName;
        this.status = TaskStatus.PENDING;
    }

    public void start() {
        this.status = TaskStatus.RUNNING;
    }

    public void complete(String finalResult) {
        this.status = TaskStatus.COMPLETED;
        this.finalResult = finalResult;
        this.failureReason = null;
    }

    public void fail(String failureReason) {
        this.status = TaskStatus.FAILED;
        this.failureReason = failureReason;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public String getUserPrompt() { return userPrompt; }
    public String getWorkingDirectory() { return workingDirectory; }
    public TaskStatus getStatus() { return status; }
    public String getModelName() { return modelName; }
    public String getFinalResult() { return finalResult; }
    public String getFailureReason() { return failureReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
