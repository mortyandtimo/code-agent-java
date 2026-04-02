package com.silvershuo.codeagent.interfaces.dto;

public class EventLogResponse {

    private Long id;
    private String eventType;
    private String eventContent;

    public EventLogResponse(Long id, String eventType, String eventContent) {
        this.id = id;
        this.eventType = eventType;
        this.eventContent = eventContent;
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public String getEventContent() { return eventContent; }
}
