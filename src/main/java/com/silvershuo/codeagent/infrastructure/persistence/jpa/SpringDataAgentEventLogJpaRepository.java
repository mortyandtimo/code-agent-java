package com.silvershuo.codeagent.infrastructure.persistence.jpa;

import com.silvershuo.codeagent.infrastructure.persistence.entity.AgentEventLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataAgentEventLogJpaRepository extends JpaRepository<AgentEventLogEntity, Long> {

    List<AgentEventLogEntity> findByTaskIdOrderByIdAsc(Long taskId);
}
