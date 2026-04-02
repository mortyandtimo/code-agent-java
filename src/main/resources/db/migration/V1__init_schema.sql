CREATE TABLE agent_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    user_prompt TEXT NOT NULL,
    working_directory VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    final_result LONGTEXT NULL,
    failure_reason TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE agent_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    message_history_json LONGTEXT NOT NULL,
    total_input_tokens BIGINT NOT NULL DEFAULT 0,
    total_output_tokens BIGINT NOT NULL DEFAULT 0,
    summary_text TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_agent_session_task FOREIGN KEY (task_id) REFERENCES agent_task (id)
);

CREATE TABLE agent_event_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_content LONGTEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_agent_event_task FOREIGN KEY (task_id) REFERENCES agent_task (id),
    CONSTRAINT fk_agent_event_session FOREIGN KEY (session_id) REFERENCES agent_session (id)
);
