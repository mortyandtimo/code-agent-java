package com.silvershuo.codeagent.interfaces.dto;

public class TaskListItemResponse {

    private final Long taskId;
    private final String title;
    private final String status;
    private final String workingDirectory;

    public TaskListItemResponse(Long taskId, String title, String status, String workingDirectory) {
        this.taskId = taskId;
        this.title = title;
        this.status = status;
        this.workingDirectory = workingDirectory;
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getTitle() {
        return title;
    }

    public String getStatus() {
        return status;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }
}
