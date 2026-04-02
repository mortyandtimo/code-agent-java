package com.silvershuo.codeagent.domain.llm.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LlmResponse {

    private final String assistantMessageJson;
    private final String finalAnswer;
    private final List<LlmToolCall> toolCalls;
    private final long inputTokens;
    private final long outputTokens;

    public LlmResponse(String assistantMessageJson, String finalAnswer, List<LlmToolCall> toolCalls, long inputTokens, long outputTokens) {
        this.assistantMessageJson = assistantMessageJson;
        this.finalAnswer = finalAnswer;
        this.toolCalls = toolCalls == null ? new ArrayList<LlmToolCall>() : toolCalls;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }

    public String getAssistantMessageJson() { return assistantMessageJson; }
    public String getFinalAnswer() { return finalAnswer; }
    public List<LlmToolCall> getToolCalls() { return Collections.unmodifiableList(toolCalls); }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public boolean hasToolCalls() { return !toolCalls.isEmpty(); }
}
