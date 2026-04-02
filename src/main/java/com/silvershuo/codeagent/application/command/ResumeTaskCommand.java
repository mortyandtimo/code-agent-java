package com.silvershuo.codeagent.application.command;

public class ResumeTaskCommand {

    private final Long taskId;

    public ResumeTaskCommand(Long taskId) {
        this.taskId = taskId;
    }

    public Long getTaskId() {
        return taskId;
    }
}
