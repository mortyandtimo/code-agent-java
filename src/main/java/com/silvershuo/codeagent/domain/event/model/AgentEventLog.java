package com.silvershuo.codeagent.domain.event.model;

import java.time.LocalDateTime;

public class AgentEventLog {

    private Long id;
    private Long taskId;
    private Long sessionId;
    private EventType eventType;
    private String eventContent;
    private LocalDateTime createdAt;

    public AgentEventLog(Long id, Long taskId, Long sessionId, EventType eventType, String eventContent) {
        this.id = id;
        this.taskId = taskId;
        this.sessionId = sessionId;
        this.eventType = eventType;
        this.eventContent = eventContent;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public Long getSessionId() { return sessionId; }
    public EventType getEventType() { return eventType; }
    public String getEventContent() { return eventContent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
