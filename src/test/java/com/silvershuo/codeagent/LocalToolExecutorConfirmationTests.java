package com.silvershuo.codeagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvershuo.codeagent.infrastructure.tool.LocalToolExecutor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

class LocalToolExecutorConfirmationTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRequireConfirmationForWindowsDeleteCommandInsideBatchExpression() throws Exception {
        LocalToolExecutor executor = new LocalToolExecutor();
        Map<String, String> arguments = new LinkedHashMap<String, String>();
        arguments.put("command", "if exist \"G:\\1\\1.txt\" (del /f /q \"G:\\1\\1.txt\" && echo DELETED) else (echo NOT_FOUND)");

        String confirmation = executor.confirmationMessage("run_shell", objectMapper.writeValueAsString(arguments), "C:\\Users\\25743\\code-agent-java");

        Assertions.assertNotNull(confirmation);
        Assertions.assertTrue(confirmation.toLowerCase().contains("del /f /q"));
    }

    @Test
    void shouldNotRequireConfirmationForBenignDirectoryListing() throws Exception {
        LocalToolExecutor executor = new LocalToolExecutor();
        Map<String, String> arguments = new LinkedHashMap<String, String>();
        arguments.put("command", "dir /b");

        String confirmation = executor.confirmationMessage("run_shell", objectMapper.writeValueAsString(arguments), "C:\\Users\\25743\\code-agent-java");

        Assertions.assertNull(confirmation);
    }
}
