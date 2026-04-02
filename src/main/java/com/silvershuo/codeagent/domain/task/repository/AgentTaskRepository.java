package com.silvershuo.codeagent.domain.task.repository;

import com.silvershuo.codeagent.domain.task.model.AgentTask;

import java.util.List;
import java.util.Optional;

public interface AgentTaskRepository {

    AgentTask save(AgentTask task);

    Optional<AgentTask> findById(Long id);

    List<AgentTask> findTop10ByOrderByIdDesc();
}
