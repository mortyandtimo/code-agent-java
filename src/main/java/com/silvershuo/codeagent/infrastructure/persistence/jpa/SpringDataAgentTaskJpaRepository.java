package com.silvershuo.codeagent.infrastructure.persistence.jpa;

import com.silvershuo.codeagent.infrastructure.persistence.entity.AgentTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataAgentTaskJpaRepository extends JpaRepository<AgentTaskEntity, Long> {

    List<AgentTaskEntity> findTop10ByOrderByIdDesc();
}
