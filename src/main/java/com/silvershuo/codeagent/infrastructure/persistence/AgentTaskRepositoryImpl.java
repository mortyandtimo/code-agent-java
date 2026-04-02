package com.silvershuo.codeagent.infrastructure.persistence;

import com.silvershuo.codeagent.domain.task.model.AgentTask;
import com.silvershuo.codeagent.domain.task.model.TaskStatus;
import com.silvershuo.codeagent.domain.task.repository.AgentTaskRepository;
import com.silvershuo.codeagent.infrastructure.persistence.entity.AgentTaskEntity;
import com.silvershuo.codeagent.infrastructure.persistence.jpa.SpringDataAgentTaskJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class AgentTaskRepositoryImpl implements AgentTaskRepository {

    private final SpringDataAgentTaskJpaRepository jpaRepository;

    public AgentTaskRepositoryImpl(SpringDataAgentTaskJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AgentTask save(AgentTask task) {
        AgentTaskEntity entity = new AgentTaskEntity();
        entity.setId(task.getId());
        entity.setTitle(task.getTitle());
        entity.setUserPrompt(task.getUserPrompt());
        entity.setWorkingDirectory(task.getWorkingDirectory());
        entity.setStatus(task.getStatus());
        entity.setModelName(task.getModelName());
        entity.setFinalResult(task.getFinalResult());
        entity.setFailureReason(task.getFailureReason());
        entity.setCreatedAt(task.getCreatedAt() == null ? LocalDateTime.now() : task.getCreatedAt());
        entity.setUpdatedAt(LocalDateTime.now());
        AgentTaskEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<AgentTask> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<AgentTask> findTop10ByOrderByIdDesc() {
        List<AgentTaskEntity> entities = jpaRepository.findTop10ByOrderByIdDesc();
        List<AgentTask> tasks = new ArrayList<AgentTask>();
        for (AgentTaskEntity entity : entities) {
            tasks.add(toDomain(entity));
        }
        return tasks;
    }

    private AgentTask toDomain(AgentTaskEntity entity) {
        AgentTask task = new AgentTask(entity.getId(), entity.getTitle(), entity.getUserPrompt(), entity.getWorkingDirectory(), entity.getModelName());
        if (entity.getStatus() == TaskStatus.RUNNING) {
            task.start();
        } else if (entity.getStatus() == TaskStatus.COMPLETED) {
            task.complete(entity.getFinalResult());
        } else if (entity.getStatus() == TaskStatus.FAILED) {
            task.fail(entity.getFailureReason());
        }
        task.setCreatedAt(entity.getCreatedAt());
        task.setUpdatedAt(entity.getUpdatedAt());
        return task;
    }
}
