package com.silvershuo.codeagent.infrastructure.persistence;

import com.silvershuo.codeagent.domain.session.model.AgentSession;
import com.silvershuo.codeagent.domain.session.repository.AgentSessionRepository;
import com.silvershuo.codeagent.infrastructure.persistence.entity.AgentSessionEntity;
import com.silvershuo.codeagent.infrastructure.persistence.jpa.SpringDataAgentSessionJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class AgentSessionRepositoryImpl implements AgentSessionRepository {

    private final SpringDataAgentSessionJpaRepository jpaRepository;

    public AgentSessionRepositoryImpl(SpringDataAgentSessionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AgentSession save(AgentSession session) {
        AgentSessionEntity entity = new AgentSessionEntity();
        entity.setId(session.getId());
        entity.setTaskId(session.getTaskId());
        entity.setMessageHistoryJson(session.getMessageHistoryJson());
        entity.setTotalInputTokens(session.getTotalInputTokens());
        entity.setTotalOutputTokens(session.getTotalOutputTokens());
        entity.setSummaryText(session.getSummaryText());
        entity.setCreatedAt(session.getCreatedAt() == null ? LocalDateTime.now() : session.getCreatedAt());
        entity.setUpdatedAt(LocalDateTime.now());
        AgentSessionEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<AgentSession> findByTaskId(Long taskId) {
        return jpaRepository.findByTaskId(taskId).map(this::toDomain);
    }

    private AgentSession toDomain(AgentSessionEntity entity) {
        AgentSession session = new AgentSession(entity.getId(), entity.getTaskId(), entity.getMessageHistoryJson());
        session.addTokenUsage(entity.getTotalInputTokens(), entity.getTotalOutputTokens());
        session.setSummaryText(entity.getSummaryText());
        session.setCreatedAt(entity.getCreatedAt());
        session.setUpdatedAt(entity.getUpdatedAt());
        return session;
    }
}
