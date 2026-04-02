package com.silvershuo.codeagent;

import com.silvershuo.codeagent.application.service.AgentApplicationService;
import com.silvershuo.codeagent.interfaces.dto.TaskResponse;
import com.silvershuo.codeagent.interfaces.rest.TaskController;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
class TaskControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentApplicationService applicationService;

    @Test
    void createTaskShouldReturnTaskResponse() throws Exception {
        Mockito.when(applicationService.createTaskAndRun(Mockito.any())).thenReturn(new TaskResponse(1L, "COMPLETED", "done", null));

        mockMvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"demo\",\"prompt\":\"analyze repo\",\"workingDirectory\":\"C:/Users/25743\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(1L))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
