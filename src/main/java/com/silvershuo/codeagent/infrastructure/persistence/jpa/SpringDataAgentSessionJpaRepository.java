package com.silvershuo.codeagent.infrastructure.persistence.jpa;

import com.silvershuo.codeagent.infrastructure.persistence.entity.AgentSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataAgentSessionJpaRepository extends JpaRepository<AgentSessionEntity, Long> {

    Optional<AgentSessionEntity> findByTaskId(Long taskId);
}
