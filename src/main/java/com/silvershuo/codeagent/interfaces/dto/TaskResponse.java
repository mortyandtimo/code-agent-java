package com.silvershuo.codeagent.interfaces.dto;

public class TaskResponse {

    private Long taskId;
    private String status;
    private String finalResult;
    private String failureReason;

    public TaskResponse() {
    }

    public TaskResponse(Long taskId, String status, String finalResult, String failureReason) {
        this.taskId = taskId;
        this.status = status;
        this.finalResult = finalResult;
        this.failureReason = failureReason;
    }

    public Long getTaskId() { return taskId; }
    public String getStatus() { return status; }
    public String getFinalResult() { return finalResult; }
    public String getFailureReason() { return failureReason; }
}
