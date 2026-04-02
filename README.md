# code-agent-java

一个面向简历项目设计的 Java 版代码任务智能体项目。这个项目定位为我在学习 AI Agent 运行机制和 tool-calling 之后，独立实现的一版 mini 复刻与工程化落地，按 `Spring Boot + MySQL + MVC + DDD` 组织为后端工程，并补了可交互 CLI。

## 项目定位

- 接收代码任务并创建 task/session
- 通过 OpenAI-compatible 接口调用模型
- 保存执行事件日志，支持过程审计
- 暴露 REST API，并提供交互式 CLI 入口
- 支持最小多轮 agent loop：LLM -> 工具执行 -> 结果回填 -> 最终回答
- 支持任务恢复：`resume` API / CLI
- 支持 OpenAI-compatible `tool_calls`、流式输出、危险操作确认
- 支持交互式命令：`/resume` 任务选择、`/tasks`、`/shell`、`!cmd`

## 当前完成度

已完成：

- Maven 项目初始化
- MVC + DDD 包结构
- MySQL/Flyway 建表脚本
- task/session/event 三类持久化模型
- REST API: `POST /api/tasks`、`POST /api/tasks/{id}/resume`、`GET /api/tasks/{id}`、`GET /api/tasks/{id}/events`
- CLI: `run --prompt ... --cwd ...` 与 `resume <taskId>`
- 多会话交互式 CLI: `chat --cwd ...`
- Slash commands: `/new`、`/clear`、`/resume`、`/tasks`、`/shell`、`/exit`
- `delete_file` 专用工具，避免单文件删除退化成脆弱的 shell 字符串拼接
- OpenAI-compatible LLM Gateway 接口与 fallback 实现
- 基于原生 `tool_calls` 的最小多轮 agent loop
- 工具分发支持：`read_file`、`write_file`、`edit_file`、`list_files`、`delete_file`、`run_shell`
- Spring Boot service test + WebMvcTest
- 失败重试、退避等待与会话历史回放

## 运行方式

1. 准备 MySQL 数据库 `code_agent_java`
2. 复制配置模板并填入你自己的参数：

```bash
copy config\config.example.env config\local.env
```

需要填写的主要项：

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `CODE_AGENT_API_KEY`
- `CODE_AGENT_LLM_BASE_URL`
- `CODE_AGENT_LLM_MODEL`

3. 构建：

```bash
mvn -DskipTests package
```

如果你想使用仓库内的本地 Maven 缓存隔离，也可以用：

```bash
mvn -s local-settings.xml -DskipTests package
```

4. 启动 REST + CLI：

```bash
cmd /c start-chat-clean.cmd
```

如果你只想启动 Spring Boot：

```bash
mvn spring-boot:run
```

交互式命令：

- `/new`：开启新会话
- `/resume`：进入最近任务选择器，恢复已有会话
- `/resume <taskId>`：按任务 id 直接切回已有任务会话
- `/tasks`：查看最近任务
- `/shell <cmd>`：直接执行本地命令
- `!<cmd>`：本地命令快捷形式
- `/exit`：退出交互式 CLI

## 目录结构

- `src/main/java`：MVC + DDD 主体代码
- `src/main/resources`：应用配置、数据库迁移、系统提示词
- `src/test/java`：WebMvc、重试、工具确认等测试
- `config/config.example.env`：通用配置模板，占位符示例
- `config/local.env`：本地私有配置文件，不应提交到远端仓库
- `docs/`：需求与执行方案文档
- `outputs/`：本地运行产物，不建议提交到远端仓库

## 协议说明

当前最小 loop 已接入 OpenAI-compatible 原生 `tool_calls`：

- 模型可直接返回结构化 `tool_calls`
- 系统会执行工具，并以 `tool` 消息回填结果
- 后续轮次会继续携带原始用户任务、assistant tool-call 消息和 tool 结果历史
- 当模型输出普通 assistant content 时，任务结束并保存最终答案

额外做了几项面向可用性的补强：

- 流式 CLI 输出
- 危险命令确认
- `run_shell` 超时后的部分结果保留
- 历史 `tool` 消息规范化，降低旧会话恢复时的协议错误概率

## 简历表述

基于 Spring Boot + MySQL 设计并实现代码任务智能体系统，采用 MVC + DDD 架构对任务、会话、工具调用与事件日志进行领域建模，支持 OpenAI-compatible 模型接入、原生 tool-calling、多轮会话回放、失败重试、流式 CLI 交互与 REST/CLI 双入口执行链路。
