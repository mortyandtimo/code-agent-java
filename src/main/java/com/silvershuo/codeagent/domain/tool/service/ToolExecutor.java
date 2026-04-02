package com.silvershuo.codeagent.domain.tool.service;

public interface ToolExecutor {

    String describeAvailableTools();

    String buildToolDefinitionsJson();

    String confirmationMessage(String toolName, String argumentsJson, String workingDirectory);

    String execute(String toolName, String argumentsJson, String workingDirectory);

    String recoverMissingToolName(String toolCallId, String argumentsJson, String toolResult, String workingDirectory);
}
