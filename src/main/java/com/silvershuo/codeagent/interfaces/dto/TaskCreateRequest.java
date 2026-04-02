package com.silvershuo.codeagent.interfaces.dto;

import javax.validation.constraints.NotBlank;

public class TaskCreateRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String prompt;

    @NotBlank
    private String workingDirectory;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getWorkingDirectory() { return workingDirectory; }
    public void setWorkingDirectory(String workingDirectory) { this.workingDirectory = workingDirectory; }
}
