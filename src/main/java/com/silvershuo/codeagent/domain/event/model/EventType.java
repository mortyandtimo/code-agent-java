package com.silvershuo.codeagent.domain.event.model;

public enum EventType {
    TASK_CREATED,
    LLM_REQUESTED,
    LLM_RETRY,
    LLM_FAILED,
    AGENT_PROGRESS,
    TOOL_CALLED,
    TOOL_RESULT_RECORDED,
    TASK_COMPLETED,
    TASK_FAILED
}
