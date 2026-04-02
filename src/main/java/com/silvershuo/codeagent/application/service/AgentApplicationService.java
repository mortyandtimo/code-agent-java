package com.silvershuo.codeagent.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvershuo.codeagent.application.command.CreateTaskCommand;
import com.silvershuo.codeagent.application.command.ContinueTaskCommand;
import com.silvershuo.codeagent.application.command.ResumeTaskCommand;
import com.silvershuo.codeagent.domain.event.model.AgentEventLog;
import com.silvershuo.codeagent.domain.event.model.EventType;
import com.silvershuo.codeagent.domain.event.repository.AgentEventLogRepository;
import com.silvershuo.codeagent.domain.llm.model.LlmResponse;
import com.silvershuo.codeagent.domain.llm.model.LlmToolCall;
import com.silvershuo.codeagent.domain.llm.service.LlmGateway;
import com.silvershuo.codeagent.domain.llm.service.LlmGatewayException;
import com.silvershuo.codeagent.domain.session.model.AgentSession;
import com.silvershuo.codeagent.domain.session.repository.AgentSessionRepository;
import com.silvershuo.codeagent.domain.task.model.AgentTask;
import com.silvershuo.codeagent.domain.task.repository.AgentTaskRepository;
import com.silvershuo.codeagent.domain.tool.service.ToolExecutor;
import com.silvershuo.codeagent.interfaces.dto.EventLogResponse;
import com.silvershuo.codeagent.interfaces.dto.TaskListItemResponse;
import com.silvershuo.codeagent.interfaces.dto.TaskResponse;
import com.silvershuo.codeagent.interfaces.cli.CliRenderContext;
import com.silvershuo.codeagent.interfaces.cli.CliRenderer;
import com.silvershuo.codeagent.shared.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AgentApplicationService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AgentTaskRepository taskRepository;
    private final AgentSessionRepository sessionRepository;
    private final AgentEventLogRepository eventLogRepository;
    private final LlmGateway llmGateway;
    private final ToolExecutor toolExecutor;

    public AgentApplicationService(AgentTaskRepository taskRepository,
                                   AgentSessionRepository sessionRepository,
                                   AgentEventLogRepository eventLogRepository,
                                   LlmGateway llmGateway,
                                   ToolExecutor toolExecutor) {
        this.taskRepository = taskRepository;
        this.sessionRepository = sessionRepository;
        this.eventLogRepository = eventLogRepository;
        this.llmGateway = llmGateway;
        this.toolExecutor = toolExecutor;
    }

    @Transactional
    public TaskResponse createTaskAndRun(CreateTaskCommand command) {
        AgentTask task = new AgentTask(null, command.getTitle(), command.getPrompt(), command.getWorkingDirectory(), "gpt-4o-mini");
        task = taskRepository.save(task);

        AgentSession session = new AgentSession(null, task.getId(), "[]");
        session = sessionRepository.save(session);
        eventLogRepository.save(new AgentEventLog(null, task.getId(), session.getId(), EventType.TASK_CREATED, "Task created"));

        task.start();
        task = taskRepository.save(task);
        return runLoop(task, session, command.getPrompt(), command.getWorkingDirectory());
    }

    @Transactional
    public TaskResponse resumeTask(ResumeTaskCommand command) {
        AgentTask task = taskRepository.findById(command.getTaskId()).orElseThrow(() -> new BizException("Task not found: " + command.getTaskId()));
        AgentSession session = sessionRepository.findByTaskId(command.getTaskId()).orElseThrow(() -> new BizException("Session not found for task: " + command.getTaskId()));
        return runLoop(task, session, "Continue the previous task from existing conversation context.", task.getWorkingDirectory());
    }

    @Transactional
    public TaskResponse continueTask(ContinueTaskCommand command) {
        AgentTask task = taskRepository.findById(command.getTaskId()).orElseThrow(() -> new BizException("Task not found: " + command.getTaskId()));
        AgentSession session = sessionRepository.findByTaskId(command.getTaskId()).orElseThrow(() -> new BizException("Session not found for task: " + command.getTaskId()));
        task.start();
        task = taskRepository.save(task);
        return runLoop(task, session, command.getPrompt(), task.getWorkingDirectory());
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(Long taskId) {
        AgentTask task = taskRepository.findById(taskId).orElseThrow(() -> new BizException("Task not found: " + taskId));
        return new TaskResponse(task.getId(), task.getStatus().name(), task.getFinalResult(), task.getFailureReason());
    }

    @Transactional(readOnly = true)
    public List<EventLogResponse> getTaskEvents(Long taskId) {
        List<AgentEventLog> logs = eventLogRepository.findByTaskId(taskId);
        List<EventLogResponse> result = new ArrayList<EventLogResponse>();
        for (AgentEventLog log : logs) {
            result.add(new EventLogResponse(log.getId(), log.getEventType().name(), log.getEventContent()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<TaskListItemResponse> listRecentTasks() {
        List<AgentTask> tasks = taskRepository.findTop10ByOrderByIdDesc();
        List<TaskListItemResponse> result = new ArrayList<TaskListItemResponse>();
        for (AgentTask task : tasks) {
            result.add(new TaskListItemResponse(task.getId(), task.getTitle(), task.getStatus().name(), task.getWorkingDirectory()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public String buildTaskContextPreview(Long taskId) {
        AgentTask task = taskRepository.findById(taskId).orElseThrow(() -> new BizException("Task not found: " + taskId));
        AgentSession session = sessionRepository.findByTaskId(taskId).orElseThrow(() -> new BizException("Session not found for task: " + taskId));
        StringBuilder builder = new StringBuilder();
        builder.append("Task #")
                .append(task.getId())
                .append(" [")
                .append(task.getStatus().name())
                .append("] ")
                .append(task.getTitle())
                .append("\n")
                .append("cwd: ")
                .append(task.getWorkingDirectory());

        List<String> excerpts = extractRecentConversationExcerpts(session.getMessageHistoryJson(), 4);
        if (excerpts.isEmpty()) {
            builder.append("\ncontext: no prior messages recorded");
        } else {
            builder.append("\ncontext:");
            for (String excerpt : excerpts) {
                builder.append("\n- ").append(excerpt);
            }
        }
        return builder.toString();
    }

    private TaskResponse runLoop(AgentTask task, AgentSession session, String initialPrompt, String workingDirectory) {
        String conversation = session.getMessageHistoryJson();
        String currentPrompt = initialPrompt;
        String finalAnswer = null;
        int explorationOnlyRounds = 0;
        boolean implementationRequested = isImplementationTask(task.getUserPrompt());
        boolean runRequested = isRunTask(task.getUserPrompt());
        boolean wroteOrEditedFiles = false;
        boolean ranAnyShellCommand = false;
        boolean attemptedRuntimeLaunch = false;
        boolean successfulVerificationObserved = false;
        boolean successfulRunObserved = false;
        String lastToolFailure = null;
        int maxRounds = runRequested ? 8 : (implementationRequested ? 7 : 6);
        int explorationBudget = implementationRequested ? 1 : 2;

        if (currentPrompt != null && !currentPrompt.trim().isEmpty()) {
            conversation = appendMessage(conversation, "user", currentPrompt, null, null);
            currentPrompt = null;
        }

        try {
            for (int round = 1; round <= maxRounds; round++) {
                eventLogRepository.save(new AgentEventLog(null, task.getId(), session.getId(), EventType.LLM_REQUESTED, "Round " + round + ", tools=" + toolExecutor.describeAvailableTools()));
                LlmResponse response = llmGateway.complete(currentPrompt, conversation == null ? "[]" : conversation, workingDirectory);
                session.addTokenUsage(response.getInputTokens(), response.getOutputTokens());

                if (response.hasToolCalls()) {
                    conversation = appendRawMessage(conversation, response.getAssistantMessageJson());
                    String progressSummary = summarizeToolCalls(response.getToolCalls());
                    eventLogRepository.save(new AgentEventLog(null, task.getId(), session.getId(), EventType.AGENT_PROGRESS,
                            progressSummary));
                    CliRenderer renderer = CliRenderContext.get();
                    if (renderer != null) {
                        renderer.printInfo(progressSummary);
                    }
                    boolean madeImplementationProgress = false;
                    boolean commandAttemptedThisRound = false;
                    boolean commandSucceededThisRound = false;
                    String toolFailureThisRound = null;
                    for (LlmToolCall toolCall : response.getToolCalls()) {
                        if (renderer != null) {
                            renderer.printToolCall(toolCall.getName(), summarizeToolCallForConsole(toolCall));
                        }
                        String confirmationMessage = toolExecutor.confirmationMessage(toolCall.getName(), toolCall.getArgumentsJson(), workingDirectory);
                        if (confirmationMessage != null && renderer != null && !renderer.confirm(confirmationMessage)) {
                            String denied = "User denied this action.";
                            eventLogRepository.save(new AgentEventLog(null, task.getId(), session.getId(), EventType.TOOL_RESULT_RECORDED, denied));
                            conversation = appendMessage(conversation, "tool", denied, toolCall.getId(), toolCall.getName());
                            toolFailureThisRound = buildToolFailureMessage(toolCall.getName(), denied);
                            lastToolFailure = toolFailureThisRound;
                            continue;
                        }
                        eventLogRepository.save(new AgentEventLog(null, task.getId(), session.getId(), EventType.TOOL_CALLED,
                                toolCall.getName() + " " + toolCall.getArgumentsJson()));
                        String toolResult = toolExecutor.execute(toolCall.getName(), toolCall.getArgumentsJson(), workingDirectory);
                        if (renderer != null) {
                            renderer.printToolResult(toolCall.getName(), summarizeToolResult(toolResult));
                        }
                        eventLogRepository.save(new AgentEventLog(null, task.getId(), session.getId(), EventType.TOOL_RESULT_RECORDED, toolResult));
                        conversation = appendMessage(conversation, "tool", toolResult, toolCall.getId(), toolCall.getName());
                        if ("write_file".equals(toolCall.getName()) || "edit_file".equals(toolCall.getName())) {
                            wroteOrEditedFiles = true;
                            madeImplementationProgress = true;
                        }
                        if ("run_shell".equals(toolCall.getName())) {
                            ranAnyShellCommand = true;
                            commandAttemptedThisRound = true;
                            String shellCommand = extractShellCommand(toolCall.getArgumentsJson());
                            boolean progressShellCommand = isProgressMakingShellCommand(shellCommand);
                            boolean runtimeShellCommand = isRuntimeLaunchCommand(shellCommand);
                            if (progressShellCommand) {
                                madeImplementationProgress = true;
                            }
                            if (runtimeShellCommand) {
                                attemptedRuntimeLaunch = true;
                            }
                            if (isSuccessfulCommandResult(toolResult)) {
                                if (progressShellCommand) {
                                    commandSucceededThisRound = true;
                                    successfulVerificationObserved = true;
                                }
                                if (runtimeShellCommand) {
                                    successfulRunObserved = true;
                                }
                            }
                        }
                        if (isToolFailure(toolResult)) {
                            toolFailureThisRound = buildToolFailureMessage(toolCall.getName(), toolResult);
                            lastToolFailure = toolFailureThisRound;
                        }
                    }
                    if (madeImplementationProgress) {
                        explorationOnlyRounds = 0;
                    } else {
                        explorationOnlyRounds++;
                    }
                    if (runRequested && successfulRunObserved) {
                        finalAnswer = wroteOrEditedFiles
                                ? "Implemented the requested runnable result, compiled it with the repo-local Maven settings, and launched it successfully."
                                : "Found an existing runnable implementation, verified it with the repo-local Maven settings, and launched it successfully.";
                        break;
                    }
                    if (implementationRequested && !runRequested && successfulVerificationObserved && !wroteOrEditedFiles) {
                        finalAnswer = "Found an existing implementation and verified it successfully.";
                        break;
                    }
                    if (toolFailureThisRound != null) {
                        currentPrompt = buildRecoveryPrompt(toolFailureThisRound, workingDirectory, runRequested);
                    } else if (commandAttemptedThisRound && commandSucceededThisRound) {
                        currentPrompt = "The verification command succeeded. Return the final answer now unless there is exactly one necessary follow-up step. Do not restart exploration.";
                    } else if (explorationOnlyRounds >= explorationBudget) {
                        currentPrompt = buildImplementationPrompt(workingDirectory, runRequested);
                    } else {
                        currentPrompt = null;
                    }
                    continue;
                }

                finalAnswer = response.getFinalAnswer();
                conversation = appendRawMessage(conversation, response.getAssistantMessageJson());
                break;
            }
        } catch (LlmGatewayException ex) {
            eventLogRepository.save(new AgentEventLog(null, task.getId(), session.getId(), EventType.LLM_FAILED,
                    "LLM failed after " + ex.getAttempts() + " attempts, retryable=" + ex.isRetryable() + ", reason=" + ex.getMessage()));
            session.overwriteMessages(conversation == null ? "[]" : conversation);
            sessionRepository.save(session);
            task.fail("LLM failed after retries: " + ex.getMessage());
            task = taskRepository.save(task);
            eventLogRepository.save(new AgentEventLog(null, task.getId(), session.getId(), EventType.TASK_FAILED, task.getFailureReason()));
            return new TaskResponse(task.getId(), task.getStatus().name(), task.getFinalResult(), task.getFailureReason());
        }

        if (implementationRequested && !wroteOrEditedFiles && !successfulVerificationObserved && !successfulRunObserved) {
            finalAnswer = "Task ended without creating or editing any files. The requested implementation was not completed.";
        }

        if (runRequested && !attemptedRuntimeLaunch) {
            finalAnswer = "Task ended without running the created result. The user explicitly asked to run or play the implementation, so this was not fully completed.";
        }

        if (runRequested && attemptedRuntimeLaunch && !successfulRunObserved && lastToolFailure != null) {
            finalAnswer = "Task ended before a successful runnable result was produced. Last failure: " + lastToolFailure;
        }

        if ((finalAnswer == null || finalAnswer.trim().isEmpty()) && successfulRunObserved) {
            finalAnswer = "Verified a runnable result successfully. The implementation was compiled with the repo-local Maven settings and the target program was launched successfully.";
        }

        if (finalAnswer == null || finalAnswer.trim().isEmpty()) {
            finalAnswer = lastToolFailure != null
                    ? "No final answer produced within loop budget. Last failure: " + lastToolFailure
                    : "No final answer produced within loop budget.";
        }

        session.overwriteMessages(conversation);
        session = sessionRepository.save(session);
        task.complete(finalAnswer);
        task = taskRepository.save(task);
        eventLogRepository.save(new AgentEventLog(null, task.getId(), session.getId(), EventType.TASK_COMPLETED, finalAnswer));
        return new TaskResponse(task.getId(), task.getStatus().name(), task.getFinalResult(), task.getFailureReason());
    }

    private String appendMessage(String conversation, String role, String content, String toolCallId, String name) {
        StringBuilder item = new StringBuilder();
        item.append("{\"role\":\"").append(role).append("\",\"content\":\"").append(escape(content)).append("\"");
        if (toolCallId != null) {
            item.append(",\"tool_call_id\":\"").append(escape(toolCallId)).append("\"");
        }
        if (name != null) {
            item.append(",\"name\":\"").append(escape(name)).append("\"");
        }
        item.append("}");
        if (conversation == null || conversation.trim().isEmpty() || "[]".equals(conversation.trim())) {
            return "[" + item.toString() + "]";
        }
        String trimmed = conversation.trim();
        return trimmed.substring(0, trimmed.length() - 1) + "," + item.toString() + "]";
    }

    private String appendRawMessage(String conversation, String rawMessageJson) {
        if (rawMessageJson == null || rawMessageJson.trim().isEmpty()) {
            return conversation == null ? "[]" : conversation;
        }
        if (conversation == null || conversation.trim().isEmpty() || "[]".equals(conversation.trim())) {
            return "[" + rawMessageJson.trim() + "]";
        }
        String trimmed = conversation.trim();
        return trimmed.substring(0, trimmed.length() - 1) + "," + rawMessageJson.trim() + "]";
    }

    private String escape(String input) {
        return input == null ? "" : input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private List<String> extractRecentConversationExcerpts(String messageHistoryJson, int maxItems) {
        List<String> excerpts = new ArrayList<String>();
        try {
            if (messageHistoryJson == null || messageHistoryJson.trim().isEmpty()) {
                return excerpts;
            }
            List<String> reversed = new ArrayList<String>();
            for (int i = objectMapper.readTree(messageHistoryJson).size() - 1; i >= 0 && reversed.size() < maxItems; i--) {
                String role = objectMapper.readTree(messageHistoryJson).get(i).path("role").asText("");
                String content = extractPreviewContent(objectMapper.readTree(messageHistoryJson).get(i).path("content"));
                if (content == null || content.trim().isEmpty()) {
                    continue;
                }
                if (!"user".equals(role) && !"assistant".equals(role) && !"tool".equals(role)) {
                    continue;
                }
                reversed.add(role + ": " + abbreviatePreview(content, 160));
            }
            for (int i = reversed.size() - 1; i >= 0; i--) {
                excerpts.add(reversed.get(i));
            }
        } catch (Exception ignored) {
        }
        return excerpts;
    }

    private String extractPreviewContent(com.fasterxml.jackson.databind.JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isTextual()) {
            return contentNode.asText("");
        }
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (com.fasterxml.jackson.databind.JsonNode node : contentNode) {
                String part = extractPreviewContent(node.path("text"));
                if (part == null || part.trim().isEmpty()) {
                    part = extractPreviewContent(node.path("content"));
                }
                if (part != null && !part.trim().isEmpty()) {
                    if (builder.length() > 0) {
                        builder.append(' ');
                    }
                    builder.append(part.trim());
                }
            }
            return builder.toString();
        }
        if (contentNode.isObject()) {
            if (contentNode.has("text")) {
                return extractPreviewContent(contentNode.path("text"));
            }
            if (contentNode.has("content")) {
                return extractPreviewContent(contentNode.path("content"));
            }
            return contentNode.toString();
        }
        return contentNode.asText("");
    }

    private String abbreviatePreview(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r", " ").replace("\n", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    private boolean isImplementationTask(String prompt) {
        if (prompt == null) {
            return false;
        }
        String lower = prompt.toLowerCase(Locale.ROOT);
        return lower.contains("write")
                || lower.contains("implement")
                || lower.contains("build")
                || lower.contains("create")
                || prompt.contains("写")
                || prompt.contains("实现")
                || prompt.contains("做一个");
    }

    private boolean isRunTask(String prompt) {
        if (prompt == null) {
            return false;
        }
        String lower = prompt.toLowerCase(Locale.ROOT);
        return lower.contains("run")
                || lower.contains("play")
                || prompt.contains("运行")
                || prompt.contains("玩");
    }

    private boolean isToolFailure(String toolResult) {
        if (toolResult == null) {
            return false;
        }
        String lower = toolResult.toLowerCase(Locale.ROOT);
        if (lower.startsWith("command timed out after") && lower.contains("partial output captured")) {
            return false;
        }
        return lower.startsWith("tool execution failed:")
                || lower.startsWith("read_file failed:")
                || lower.startsWith("write_file failed:")
                || lower.startsWith("edit_file failed:")
                || lower.startsWith("list_files failed:")
                || lower.startsWith("run_shell failed:")
                || lower.startsWith("command failed with exit code")
                || lower.contains("accessdeniedexception")
                || lower.contains("error:")
                || lower.contains("exception:");
    }

    private boolean isSuccessfulCommandResult(String toolResult) {
        if (toolResult == null) {
            return false;
        }
        return toolResult.toLowerCase(Locale.ROOT).startsWith("command succeeded with exit code 0");
    }

    private String buildToolFailureMessage(String toolName, String toolResult) {
        String normalized = toolResult == null ? "unknown failure" : toolResult.replace("\r", "").trim();
        if (normalized.length() > 500) {
            normalized = normalized.substring(0, 500) + "...";
        }
        return toolName + " => " + normalized;
    }

    private String buildImplementationPrompt(String workingDirectory, boolean runRequested) {
        StringBuilder builder = new StringBuilder();
        builder.append("You already have enough context. Stop exploring and make concrete progress now. ");
        builder.append("Use write_file or edit_file to implement the requested change. ");
        if (runRequested) {
            builder.append("Then verify it with run_shell. ");
        }
        builder.append("In this repository, Java and Maven commands must use `mvn -s local-settings.xml ...` from ")
                .append(workingDirectory)
                .append(" so Maven uses the repo-local cache. ");
        builder.append("Do not call list_files or read_file again unless one missing fact blocks the implementation.");
        return builder.toString();
    }

    private String buildRecoveryPrompt(String toolFailure, String workingDirectory, boolean runRequested) {
        StringBuilder builder = new StringBuilder();
        builder.append("The previous tool call failed: ").append(toolFailure).append(" ");
        builder.append("Recover from this exact failure now instead of restarting exploration. ");
        builder.append("If you need Maven in this repository, use `mvn -s local-settings.xml ...` from ")
                .append(workingDirectory)
                .append(" so dependencies resolve through the repo-local cache. ");
        if (runRequested) {
            builder.append("The user asked for a runnable result, so fix the command and run it successfully before finishing. ");
        }
        builder.append("Return a final answer as soon as the implementation is verified.");
        return builder.toString();
    }

    private String summarizeToolCalls(List<LlmToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return "模型准备直接回答";
        }
        List<String> steps = new ArrayList<String>();
        for (LlmToolCall toolCall : toolCalls) {
            String toolName = toolCall.getName();
            if ("read_file".equals(toolName)) {
                steps.add("读取文件");
            } else if ("list_files".equals(toolName)) {
                steps.add("查看目录");
            } else if ("write_file".equals(toolName)) {
                steps.add("新建文件");
            } else if ("edit_file".equals(toolName)) {
                steps.add("修改文件");
            } else if ("run_shell".equals(toolName)) {
                steps.add("执行命令");
            } else {
                steps.add(toolName);
            }
        }
        return "当前动作: " + String.join(" -> ", steps);
    }

    private String summarizeToolCallForConsole(LlmToolCall toolCall) {
        String toolName = toolCall.getName();
        String argumentsJson = toolCall.getArgumentsJson();
        if ("read_file".equals(toolName) || "list_files".equals(toolName) || "write_file".equals(toolName) || "edit_file".equals(toolName)) {
            return extractArgument(argumentsJson, "path");
        }
        if ("run_shell".equals(toolName)) {
            return abbreviate(extractArgument(argumentsJson, "command"), 96);
        }
        return "";
    }

    private String summarizeToolResult(String toolResult) {
        if (toolResult == null || toolResult.trim().isEmpty()) {
            return "(no output)";
        }
        String normalized = toolResult.replace("\r", "").trim();
        int newLineIndex = normalized.indexOf('\n');
        String firstLine = newLineIndex >= 0 ? normalized.substring(0, newLineIndex) : normalized;
        return abbreviate(firstLine, 120);
    }

    private String extractArgument(String argumentsJson, String fieldName) {
        try {
            return objectMapper.readTree(argumentsJson).path(fieldName).asText("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r", " ").replace("\n", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    private String extractShellCommand(String argumentsJson) {
        try {
            return objectMapper.readTree(argumentsJson).path("command").asText("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean isProgressMakingShellCommand(String command) {
        if (command == null) {
            return false;
        }
        String lower = command.toLowerCase(Locale.ROOT);
        return lower.contains("mvn ")
                || lower.contains("gradle")
                || lower.contains("java ")
                || lower.contains("javaw ")
                || lower.contains("python ")
                || lower.contains("pytest")
                || lower.contains("node ")
                || lower.contains("npm ")
                || lower.contains("pnpm ")
                || lower.contains("yarn ")
                || lower.contains("go test")
                || lower.contains("cargo ");
    }

    private boolean isRuntimeLaunchCommand(String command) {
        if (command == null) {
            return false;
        }
        String lower = command.toLowerCase(Locale.ROOT);
        return lower.contains("java ")
                || lower.contains("javaw ")
                || lower.contains("python ")
                || lower.contains("node ")
                || lower.contains("npm start")
                || lower.contains("pnpm dev")
                || lower.contains("pnpm start")
                || lower.contains("yarn start")
                || lower.contains("spring-boot:run");
    }
}
