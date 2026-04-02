package com.silvershuo.codeagent.interfaces.cli;

public interface CliRenderer {

    void resetTurn();

    void printWelcome(String cwd);

    String promptText();

    void printAssistantText(String text);

    void printToolCall(String name, String summary);

    void printToolResult(String name, String summary);

    void printRetry(int attempt, int maxAttempts, String reason);

    void printInfo(String message);

    void printError(String message);

    void printDivider();

    String commandGuideText();

    String infoText(String message);

    boolean confirm(String message);

    boolean hasPrintedAssistantText();
}
