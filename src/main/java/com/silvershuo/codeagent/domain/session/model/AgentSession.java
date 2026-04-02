package com.silvershuo.codeagent.domain.session.model;

import java.time.LocalDateTime;

public class AgentSession {

    private Long id;
    private Long taskId;
    private String messageHistoryJson;
    private long totalInputTokens;
    private long totalOutputTokens;
    private String summaryText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AgentSession(Long id, Long taskId, String messageHistoryJson) {
        this.id = id;
        this.taskId = taskId;
        this.messageHistoryJson = messageHistoryJson;
    }

    public void overwriteMessages(String messageHistoryJson) {
        this.messageHistoryJson = messageHistoryJson;
    }

    public void addTokenUsage(long inputTokens, long outputTokens) {
        this.totalInputTokens += inputTokens;
        this.totalOutputTokens += outputTokens;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public String getMessageHistoryJson() { return messageHistoryJson; }
    public long getTotalInputTokens() { return totalInputTokens; }
    public long getTotalOutputTokens() { return totalOutputTokens; }
    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
