package com.silvershuo.codeagent.domain.llm.model;

public class LlmToolCall {

    private final String id;
    private final String name;
    private final String argumentsJson;

    public LlmToolCall(String id, String name, String argumentsJson) {
        this.id = id;
        this.name = name;
        this.argumentsJson = argumentsJson;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getArgumentsJson() { return argumentsJson; }
}
