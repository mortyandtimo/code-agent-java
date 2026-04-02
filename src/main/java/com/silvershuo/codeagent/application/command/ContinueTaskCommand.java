package com.silvershuo.codeagent.application.command;

public class ContinueTaskCommand {

    private final Long taskId;
    private final String prompt;

    public ContinueTaskCommand(Long taskId, String prompt) {
        this.taskId = taskId;
        this.prompt = prompt;
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getPrompt() {
        return prompt;
    }
}
