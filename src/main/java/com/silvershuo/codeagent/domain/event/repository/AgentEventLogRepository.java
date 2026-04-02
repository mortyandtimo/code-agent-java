package com.silvershuo.codeagent.domain.event.repository;

import com.silvershuo.codeagent.domain.event.model.AgentEventLog;

import java.util.List;

public interface AgentEventLogRepository {

    AgentEventLog save(AgentEventLog eventLog);

    List<AgentEventLog> findByTaskId(Long taskId);
}
