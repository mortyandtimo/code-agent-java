package com.silvershuo.codeagent.infrastructure.persistence;

import com.silvershuo.codeagent.domain.event.model.AgentEventLog;
import com.silvershuo.codeagent.domain.event.repository.AgentEventLogRepository;
import com.silvershuo.codeagent.infrastructure.persistence.entity.AgentEventLogEntity;
import com.silvershuo.codeagent.infrastructure.persistence.jpa.SpringDataAgentEventLogJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class AgentEventLogRepositoryImpl implements AgentEventLogRepository {

    private final SpringDataAgentEventLogJpaRepository jpaRepository;

    public AgentEventLogRepositoryImpl(SpringDataAgentEventLogJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AgentEventLog save(AgentEventLog eventLog) {
        AgentEventLogEntity entity = new AgentEventLogEntity();
        entity.setId(eventLog.getId());
        entity.setTaskId(eventLog.getTaskId());
        entity.setSessionId(eventLog.getSessionId());
        entity.setEventType(eventLog.getEventType());
        entity.setEventContent(eventLog.getEventContent());
        entity.setCreatedAt(eventLog.getCreatedAt() == null ? LocalDateTime.now() : eventLog.getCreatedAt());
        AgentEventLogEntity saved = jpaRepository.save(entity);
        AgentEventLog log = new AgentEventLog(saved.getId(), saved.getTaskId(), saved.getSessionId(), saved.getEventType(), saved.getEventContent());
        log.setCreatedAt(saved.getCreatedAt());
        return log;
    }

    @Override
    public List<AgentEventLog> findByTaskId(Long taskId) {
        List<AgentEventLogEntity> entities = jpaRepository.findByTaskIdOrderByIdAsc(taskId);
        List<AgentEventLog> result = new ArrayList<AgentEventLog>();
        for (AgentEventLogEntity entity : entities) {
            AgentEventLog log = new AgentEventLog(entity.getId(), entity.getTaskId(), entity.getSessionId(), entity.getEventType(), entity.getEventContent());
            log.setCreatedAt(entity.getCreatedAt());
            result.add(log);
        }
        return result;
    }
}
