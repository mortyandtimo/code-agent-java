package com.silvershuo.codeagent.domain.session.repository;

import com.silvershuo.codeagent.domain.session.model.AgentSession;

import java.util.Optional;

public interface AgentSessionRepository {

    AgentSession save(AgentSession session);

    Optional<AgentSession> findByTaskId(Long taskId);
}
