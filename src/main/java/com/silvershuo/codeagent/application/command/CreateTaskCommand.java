package com.silvershuo.codeagent.application.command;

public class CreateTaskCommand {

    private final String title;
    private final String prompt;
    private final String workingDirectory;

    public CreateTaskCommand(String title, String prompt, String workingDirectory) {
        this.title = title;
        this.prompt = prompt;
        this.workingDirectory = workingDirectory;
    }

    public String getTitle() { return title; }
    public String getPrompt() { return prompt; }
    public String getWorkingDirectory() { return workingDirectory; }
}
