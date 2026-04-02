# code-agent-java

一个用 Java 实现的代码任务智能体项目。

项目尝试用 `Spring Boot + MySQL + DDD` 去复现一个最小但完整的 coding agent：任务如何创建、上下文如何累积、工具如何执行、失败如何恢复、会话如何继续。

采用 Agent Loop ，从“会调用 API”推进到“能把一个 agent 当作后端系统来设计和迭代”。

## Why

agent demo 的重点是“模型能不能调工具”，但落地时，问题往往不在单次调用，而在循环本身：

- 一个任务怎么被建模，而不是只当作一条 prompt
- 一次工具调用失败后，系统该如何恢复，而不是把异常直接抛给用户
- 会话恢复时，历史消息怎样重新组织，才能继续满足协议
- 危险操作应该拦在哪一层，而不是完全依赖模型自觉
- CLI 交互体验怎样做，用户才会把它当成 agent，而不是命令拼接器

这个项目的目标不是“再做一个聊天后端”，而是做一个可以运行、可以恢复、可以审计、可以继续迭代的 code agent skeleton。

## Agent Loop

这个项目里，我对 Agent Loop 的理解逐渐收敛成下面几个设计判断。

### 1. Agent 的核心不是一次回答，而是一个可恢复的循环

真正的 agent 不是“模型输出文本”就结束，而是：

`user input -> llm -> tool call -> tool result -> llm -> ... -> final answer`

这个循环必须能停、能继续、能失败、能恢复，否则它只是一个会调工具的聊天接口。

在这个项目里，`task / session / event log` 被当成一等公民建模，就是因为我更关心“循环是否稳定”，而不是“单轮回复是否漂亮”。

### 2. Tool result 不是附加信息，而是协议状态的一部分

我在实现里比较明确地把 `assistant tool_calls` 和 `tool` 消息当作上下文协议的一部分来处理，而不是简单把工具结果拼进下一轮 prompt。

这背后的想法是：

- tool call 要可追踪
- tool result 要可回放
- 恢复旧任务时，消息结构必须仍然合法

所以这个项目里专门做了历史消息规范化、缺失 `tool.name` 的恢复，以及旧会话重新注入时的结构修复。这个点很工程，但它决定了 agent 能不能真的“接着干”。

### 3. Agent Loop 的质量，很多时候取决于失败恢复策略

一个真实 agent 不可能只走 happy path。

系统不能在工具失败之后重新做一轮大范围探索，应该围绕“刚才到底哪里失败了”去恢复。所以现在的 loop 里会根据上一次工具失败内容，生成针对性的 recovery prompt，避免模型重新开始摸索。

对比发现带来的直接收益是：

- 失败后上下文不会漂
- 工具调用不会无意义重复
- 模型更容易围绕 exact failure 修正动作

### 4. 安全边界应该落在执行层，而不是只写进提示词

如果一个 agent 可以写文件、删文件、跑命令，那安全策略不能只靠 system prompt 约束。
把危险动作确认放在了工具执行边界：

- 危险 shell 命令先拦截
- 单文件删除走 `delete_file` 专用工具，而不是放任模型自由拼 shell
- CLI 中明确做用户确认

提示词可以降低风险，但真正可靠的边界一定要落在 executor 层。

### 5. 长上下文当然重要，但上下文“结构正确”更重要

在做恢复、流式输出、工具回填环节，可知：agent 不是单纯拼更多 token 就更强。

如果历史消息结构错了，或者工具消息缺字段，模型即使能吃很长上下文，也会直接在协议层失败。相比“盲目堆上下文”，因此需要更重视：

- 消息结构是否合法
- 恢复后的对话能不能继续执行
- 工具结果是不是被作为协议状态而不是噪声

这也是为什么这个项目会把 `resume` 和历史规范化当成核心能力，而不是一个附加命令。

## Architecture

项目整体按 DDD 组织。

- `application`
  - 编排任务创建、继续执行、恢复执行
  - 持有 agent loop 主流程
- `domain`
  - `task / session / event / llm / tool` 等核心模型与仓储接口
- `infrastructure`
  - OpenAI-compatible LLM gateway
  - 本地工具执行器
  - JPA 持久化实现
  - system prompt 构建
- `interfaces`
  - REST API
  - 交互式 CLI

## What Is Implemented

当前版本已经打通了一条最小但完整的执行链路：

- 任务创建、恢复、继续执行
- `task / session / event log` 三类持久化模型
- OpenAI-compatible `chat/completions` 接入
- 原生 `tool_calls` 解析与回填
- 流式 CLI 输出
- REST + 交互式 CLI 双入口
- 工具执行：`read_file`、`write_file`、`edit_file`、`list_files`、`delete_file`、`run_shell`
- 失败重试与指数退避
- 危险操作确认
- `run_shell` 超时后的部分结果保留
- 历史消息规范化与 `resume` 会话恢复

loop 闭环：

- 有任务开始
- 有中间状态
- 有工具执行
- 有失败恢复
- 有最终结果
- 有会话继续

## Public API And CLI

REST API：

- `POST /api/tasks`
- `POST /api/tasks/{id}/resume`
- `GET /api/tasks/{id}`
- `GET /api/tasks/{id}/events`

CLI：

- `chat --cwd ...`
- `run --prompt ... --cwd ...`
- `resume <taskId>`

交互式命令：

- `/new`
- `/clear`
- `/resume`
- `/resume <taskId>`
- `/tasks`
- `/shell <cmd>`
- `!<cmd>`
- `/exit`

## Runtime Notes

1. 准备 MySQL 数据库 `code_agent_java`
2. 复制配置模板并填入自己的参数

```bash
copy config\config.example.env config\local.env
```

主要配置项：

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `CODE_AGENT_API_KEY`
- `CODE_AGENT_LLM_BASE_URL`
- `CODE_AGENT_LLM_MODEL`

构建：

```bash
mvn -DskipTests package
```

如果想用仓库内本地 Maven 缓存隔离：

```bash
mvn -s local-settings.xml -DskipTests package
```

启动交互式 CLI：

```bash
cmd /c start-chat-clean.cmd
```

仅启动 Spring Boot：

```bash
mvn spring-boot:run
```

## Iteration Notes

这个项目几个比较关键的点：

- 不是所有失败都该当作“重新思考”，很多时候应该围绕 exact failure 做恢复
- Windows 路径和 CLI 输入层的问题，会直接污染 agent 工具层
- 删除这类高风险动作不能交给模型自由生成 shell 命令
- 会话恢复真正难的地方，不是把历史读出来，而是把历史重新组织成下一轮还能吃的协议消息
- 流式输出不是 UI 小功能，它会反过来影响用户如何感知 agent 是否还在正确推进任务

## Structure

- `src/main/java`：DDD 主体代码
- `src/main/resources`：配置、数据库迁移、系统提示词
- `src/test/java`：控制器、重试、工具确认等测试
- `config/config.example.env`：通用配置模板
- `config/local.env`：本地私有配置，不提交远端
- `docs/`：需求与执行方案文档
- `outputs/`：本地运行产物
