package com.silvershuo.codeagent;

import com.silvershuo.codeagent.application.command.CreateTaskCommand;
import com.silvershuo.codeagent.application.command.ContinueTaskCommand;
import com.silvershuo.codeagent.application.command.ResumeTaskCommand;
import com.silvershuo.codeagent.application.service.AgentApplicationService;
import com.silvershuo.codeagent.domain.event.model.AgentEventLog;
import com.silvershuo.codeagent.domain.event.repository.AgentEventLogRepository;
import com.silvershuo.codeagent.domain.llm.model.LlmResponse;
import com.silvershuo.codeagent.domain.llm.model.LlmToolCall;
import com.silvershuo.codeagent.domain.session.model.AgentSession;
import com.silvershuo.codeagent.domain.session.repository.AgentSessionRepository;
import com.silvershuo.codeagent.domain.task.model.AgentTask;
import com.silvershuo.codeagent.domain.task.repository.AgentTaskRepository;
import com.silvershuo.codeagent.interfaces.dto.TaskListItemResponse;
import com.silvershuo.codeagent.interfaces.dto.TaskResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
@ActiveProfiles("test")
class CodeAgentApplicationTests {

    @MockBean
    private AgentTaskRepository taskRepository;

    @MockBean
    private AgentSessionRepository sessionRepository;

    @MockBean
    private AgentEventLogRepository eventLogRepository;

    @MockBean
    private com.silvershuo.codeagent.domain.llm.service.LlmGateway llmGateway;

    @MockBean
    private com.silvershuo.codeagent.domain.tool.service.ToolExecutor toolExecutor;

    @MockBean
    private com.silvershuo.codeagent.interfaces.cli.AgentCliRunner cliRunner;

    @javax.annotation.Resource
    private AgentApplicationService applicationService;

    @Test
    void createTaskAndResumeShouldWorkWithStructuredToolCalls() {
        AgentTask savedTask = new AgentTask(1L, "title", "prompt", ".", "gpt-4o-mini");
        savedTask.start();
        AgentSession savedSession = new AgentSession(10L, 1L, "[]");
        List<String> histories = new ArrayList<String>();

        Mockito.when(taskRepository.save(Mockito.any(AgentTask.class))).thenAnswer(invocation -> {
            AgentTask task = invocation.getArgument(0);
            if (task.getId() == null) {
                task.setId(1L);
            }
            return task;
        });
        Mockito.when(taskRepository.findById(1L)).thenReturn(Optional.of(savedTask));
        Mockito.when(sessionRepository.save(Mockito.any(AgentSession.class))).thenAnswer(invocation -> {
            AgentSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(10L);
            }
            return session;
        });
        Mockito.when(sessionRepository.findByTaskId(1L)).thenReturn(Optional.of(savedSession));
        Mockito.when(eventLogRepository.save(Mockito.any(AgentEventLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(eventLogRepository.findByTaskId(1L)).thenReturn(Collections.emptyList());
        Mockito.when(toolExecutor.describeAvailableTools()).thenReturn("read_file, list_files, run_shell");
        Mockito.when(toolExecutor.execute(Mockito.eq("list_files"), Mockito.anyString(), Mockito.eq("."))).thenReturn("README.md\npom.xml");

        List<LlmToolCall> toolCalls = new ArrayList<LlmToolCall>();
        toolCalls.add(new LlmToolCall("call-1", "list_files", "{\"path\":\".\"}"));
        AtomicInteger callIndex = new AtomicInteger(0);
        Mockito.when(llmGateway.complete(Mockito.nullable(String.class), Mockito.anyString(), Mockito.anyString())).thenAnswer(invocation -> {
            histories.add(invocation.getArgument(1));
            int current = callIndex.getAndIncrement();
            if (current == 0) {
                return new LlmResponse("{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"list_files\",\"arguments\":\"{\\\"path\\\":\\\".\\\"}\"}}]}", null, toolCalls, 1L, 1L);
            }
            return new LlmResponse("{\"role\":\"assistant\",\"content\":\"FINAL OK\"}", "FINAL OK", Collections.<LlmToolCall>emptyList(), 1L, 1L);
        });

        TaskResponse created = applicationService.createTaskAndRun(new CreateTaskCommand("title", "prompt", "."));
        Assertions.assertEquals("COMPLETED", created.getStatus());
        Assertions.assertEquals("FINAL OK", created.getFinalResult());
        Assertions.assertTrue(histories.get(0).contains("\"role\":\"user\""));
        Assertions.assertTrue(histories.get(0).contains("\"content\":\"prompt\""));
        Assertions.assertTrue(histories.get(1).contains("\"tool_calls\""));
        Assertions.assertTrue(histories.get(1).contains("\"tool_call_id\":\"call-1\""));
        Assertions.assertTrue(histories.get(1).contains("\"content\":\"prompt\""));

        TaskResponse resumed = applicationService.resumeTask(new ResumeTaskCommand(1L));
        Assertions.assertEquals("COMPLETED", resumed.getStatus());

        Mockito.when(taskRepository.findTop10ByOrderByIdDesc()).thenReturn(Collections.singletonList(savedTask));
        List<TaskListItemResponse> tasks = applicationService.listRecentTasks();
        Assertions.assertEquals(1, tasks.size());
        Assertions.assertEquals(Long.valueOf(1L), tasks.get(0).getTaskId());

        TaskResponse continued = applicationService.continueTask(new ContinueTaskCommand(1L, "continue this task"));
        Assertions.assertEquals("COMPLETED", continued.getStatus());
    }
}
