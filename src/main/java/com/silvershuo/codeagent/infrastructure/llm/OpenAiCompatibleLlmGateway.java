package com.silvershuo.codeagent.infrastructure.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvershuo.codeagent.domain.llm.model.LlmResponse;
import com.silvershuo.codeagent.domain.llm.model.LlmToolCall;
import com.silvershuo.codeagent.domain.llm.service.LlmGateway;
import com.silvershuo.codeagent.domain.llm.service.LlmGatewayException;
import com.silvershuo.codeagent.infrastructure.config.LlmProperties;
import com.silvershuo.codeagent.infrastructure.prompt.SystemPromptBuilder;
import com.silvershuo.codeagent.infrastructure.tool.LocalToolExecutor;
import com.silvershuo.codeagent.interfaces.cli.CliRenderContext;
import com.silvershuo.codeagent.interfaces.cli.CliRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OpenAiCompatibleLlmGateway implements LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmGateway.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final LlmProperties llmProperties;
    private final LocalToolExecutor toolExecutor;
    private final SystemPromptBuilder systemPromptBuilder;

    public OpenAiCompatibleLlmGateway(RestTemplate restTemplate, ObjectMapper objectMapper, LlmProperties llmProperties, LocalToolExecutor toolExecutor, SystemPromptBuilder systemPromptBuilder) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.llmProperties = llmProperties;
        this.toolExecutor = toolExecutor;
        this.systemPromptBuilder = systemPromptBuilder;
    }

    @Override
    public LlmResponse complete(String prompt, String messageHistoryJson, String workingDirectory) {
        if (llmProperties.getApiKey() == null || llmProperties.getApiKey().trim().isEmpty()) {
            return fallback(prompt);
        }
        int maxAttempts = Math.max(1, llmProperties.getMaxRetries() + 1);
        String requestId = UUID.randomUUID().toString();
        Exception last = null;
        boolean retryable = false;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                long startedAt = System.currentTimeMillis();
                LlmResponse response = doComplete(prompt, messageHistoryJson, workingDirectory, requestId, attempt, maxAttempts);
                if (attempt > 1) {
                    log.info("LLM request {} succeeded on attempt {}/{} after {} ms", requestId, attempt, maxAttempts, System.currentTimeMillis() - startedAt);
                }
                return response;
            } catch (Exception ex) {
                last = ex;
                retryable = isRetryable(ex);
                log.warn("LLM request {} failed on attempt {}/{}: {}", requestId, attempt, maxAttempts, ex.getMessage());
                if (!retryable || attempt >= maxAttempts) {
                    break;
                }
                CliRenderer renderer = CliRenderContext.get();
                if (renderer != null) {
                    renderer.printRetry(attempt, maxAttempts - 1, ex.getMessage());
                }
                sleep(attempt);
            }
        }
        throw new LlmGatewayException(last == null ? "LLM request failed" : last.getMessage(), retryable, maxAttempts, last);
    }

    private LlmResponse doComplete(String prompt, String messageHistoryJson, String workingDirectory, String requestId, int attempt, int maxAttempts) throws Exception {
        String url = llmProperties.getBaseUrl();
        if (!url.endsWith("/chat/completions")) {
            url = url.endsWith("/") ? url + "chat/completions" : url + "/chat/completions";
        }

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("model", llmProperties.getModel());
        payload.put("temperature", 0.2);
        payload.put("tools", objectMapper.readTree(toolExecutor.buildToolDefinitionsJson()));
        payload.put("tool_choice", "auto");
        payload.put("messages", buildMessages(prompt, messageHistoryJson, workingDirectory));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmProperties.getApiKey());
        headers.set("User-Agent", "Java-Code-Agent/0.1");
        headers.set("X-Code-Agent-Request-Id", requestId);
        headers.set("X-Code-Agent-Attempt", attempt + "/" + maxAttempts);

        CliRenderer renderer = CliRenderContext.get();
        if (renderer != null) {
            payload.put("stream", true);
            payload.put("stream_options", objectMapper.createObjectNode().put("include_usage", true));
            String responseBody = streamChatCompletion(url, headers, payload, renderer);
            return parseChatCompletion(responseBody);
        }

        String body = objectMapper.writeValueAsString(payload);
        String responseBody = restTemplate.postForObject(url, new HttpEntity<String>(body, headers), String.class);
        return parseChatCompletion(responseBody);
    }

    private LlmResponse parseChatCompletion(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode message = root.path("choices").path(0).path("message");

        List<LlmToolCall> toolCalls = new ArrayList<LlmToolCall>();
        JsonNode toolCallsNode = message.path("tool_calls");
        if (toolCallsNode.isArray()) {
            for (JsonNode node : toolCallsNode) {
                toolCalls.add(new LlmToolCall(
                        node.path("id").asText(),
                        node.path("function").path("name").asText(),
                        node.path("function").path("arguments").asText("{}")
                ));
            }
        }

        String answer = message.path("content").isNull() ? null : message.path("content").asText("");
        long inputTokens = root.path("usage").path("prompt_tokens").asLong(0L);
        long outputTokens = root.path("usage").path("completion_tokens").asLong(0L);
        return new LlmResponse(objectMapper.writeValueAsString(message), answer, toolCalls, inputTokens, outputTokens);
    }

    private String streamChatCompletion(String url, HttpHeaders headers, Map<String, Object> payload, CliRenderer renderer) throws Exception {
        HttpHeaders streamHeaders = new HttpHeaders();
        streamHeaders.putAll(headers);
        ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<String>(objectMapper.writeValueAsString(payload), streamHeaders),
                byte[].class
        );

        byte[] bodyBytes = response.getBody();
        if (bodyBytes == null) {
            throw new IllegalStateException("Empty streaming response body");
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bodyBytes), "UTF-8"));
        StringBuilder eventData = new StringBuilder();
        StringBuilder content = new StringBuilder();
        Map<Integer, ToolCallAccumulator> toolCalls = new LinkedHashMap<Integer, ToolCallAccumulator>();
        long promptTokens = 0L;
        long completionTokens = 0L;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("data: ")) {
                String data = line.substring(6);
                if ("[DONE]".equals(data)) {
                    break;
                }
                eventData.append(data);
                continue;
            }
            if (!line.trim().isEmpty()) {
                continue;
            }
            if (eventData.length() == 0) {
                continue;
            }

            JsonNode chunk = objectMapper.readTree(eventData.toString());
            if (chunk.path("usage").isObject()) {
                promptTokens = chunk.path("usage").path("prompt_tokens").asLong(promptTokens);
                completionTokens = chunk.path("usage").path("completion_tokens").asLong(completionTokens);
            }

            JsonNode choices = chunk.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).path("delta");
                if (delta.has("content") && !delta.get("content").isNull()) {
                    String piece = delta.get("content").asText("");
                    if (!piece.isEmpty()) {
                        content.append(piece);
                        renderer.printAssistantText(piece);
                    }
                }
                JsonNode toolCallNodes = delta.path("tool_calls");
                if (toolCallNodes.isArray()) {
                    for (JsonNode node : toolCallNodes) {
                        int index = node.path("index").asInt();
                        ToolCallAccumulator accumulator = toolCalls.get(index);
                        if (accumulator == null) {
                            accumulator = new ToolCallAccumulator();
                            toolCalls.put(index, accumulator);
                        }
                        if (node.has("id")) {
                            accumulator.id = node.path("id").asText(accumulator.id);
                        }
                        JsonNode functionNode = node.path("function");
                        if (functionNode.isObject()) {
                            JsonNode nameNode = functionNode.get("name");
                            if (nameNode != null && !nameNode.isNull()) {
                                String namePiece = nameNode.asText("");
                                if (!namePiece.isEmpty()) {
                                    accumulator.name = namePiece;
                                }
                            }
                            JsonNode argumentsNode = functionNode.get("arguments");
                            if (argumentsNode != null && !argumentsNode.isNull()) {
                                accumulator.arguments.append(argumentsNode.asText(""));
                            }
                        }
                    }
                }
            }
            eventData.setLength(0);
        }

        Map<String, Object> message = new LinkedHashMap<String, Object>();
        message.put("role", "assistant");
        if (content.length() > 0) {
            message.put("content", content.toString());
        } else {
            message.put("content", null);
        }
        if (!toolCalls.isEmpty()) {
            List<Map<String, Object>> toolCallNodes = new ArrayList<Map<String, Object>>();
            for (ToolCallAccumulator accumulator : toolCalls.values()) {
                Map<String, Object> function = new LinkedHashMap<String, Object>();
                function.put("name", accumulator.name == null ? "" : accumulator.name);
                function.put("arguments", accumulator.arguments.toString());
                Map<String, Object> toolNode = new LinkedHashMap<String, Object>();
                toolNode.put("id", accumulator.id == null ? "" : accumulator.id);
                toolNode.put("type", "function");
                toolNode.put("function", function);
                toolCallNodes.add(toolNode);
            }
            message.put("tool_calls", toolCallNodes);
        }

        Map<String, Object> usage = new LinkedHashMap<String, Object>();
        usage.put("prompt_tokens", promptTokens);
        usage.put("completion_tokens", completionTokens);

        Map<String, Object> choice = new LinkedHashMap<String, Object>();
        choice.put("message", message);

        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("choices", java.util.Collections.singletonList(choice));
        root.put("usage", usage);
        return objectMapper.writeValueAsString(root);
    }

    private static class ToolCallAccumulator {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();
    }

    private List<Map<String, Object>> buildMessages(String prompt, String messageHistoryJson, String workingDirectory) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();

        Map<String, Object> system = new LinkedHashMap<String, Object>();
        system.put("role", "system");
        system.put("content", systemPromptBuilder.build(workingDirectory));
        messages.add(system);

        JsonNode history = objectMapper.readTree(messageHistoryJson == null || messageHistoryJson.trim().isEmpty() ? "[]" : messageHistoryJson);
        if (history.isArray()) {
            for (JsonNode node : history) {
                Map<String, Object> normalized = normalizeHistoryMessage(node, workingDirectory);
                if (normalized != null && !normalized.isEmpty()) {
                    messages.add(normalized);
                }
            }
        }

        if (prompt != null && !prompt.trim().isEmpty()) {
            Map<String, Object> user = new LinkedHashMap<String, Object>();
            user.put("role", "user");
            user.put("content", prompt);
            messages.add(user);
        }
        return messages;
    }

    private Map<String, Object> normalizeHistoryMessage(JsonNode node, String workingDirectory) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String role = node.path("role").asText("");
        if (role == null || role.trim().isEmpty()) {
            return null;
        }

        Map<String, Object> message = new LinkedHashMap<String, Object>();
        message.put("role", role);

        if ("tool".equals(role)) {
            message.put("content", normalizeMessageContent(node.path("content")));
            if (node.has("tool_call_id") && !node.path("tool_call_id").asText("").trim().isEmpty()) {
                message.put("tool_call_id", node.path("tool_call_id").asText());
            }
            String toolName = node.path("name").asText("");
            if (toolName == null || toolName.trim().isEmpty()) {
                toolName = toolExecutor.recoverMissingToolName(
                        node.path("tool_call_id").asText(""),
                        null,
                        node.path("content").asText(""),
                        workingDirectory
                );
            }
            if (toolName != null && !toolName.trim().isEmpty()) {
                message.put("name", toolName);
            }
            return message;
        }

        Object content = normalizeMessageContent(node.path("content"));
        message.put("content", content);

        if ("assistant".equals(role) && node.path("tool_calls").isArray()) {
            List<Map<String, Object>> toolCalls = new ArrayList<Map<String, Object>>();
            for (JsonNode toolNode : node.path("tool_calls")) {
                Map<String, Object> toolCall = new LinkedHashMap<String, Object>();
                toolCall.put("id", toolNode.path("id").asText(""));
                toolCall.put("type", toolNode.path("type").asText("function"));
                JsonNode functionNode = toolNode.path("function");
                Map<String, Object> function = new LinkedHashMap<String, Object>();
                function.put("name", functionNode.path("name").asText(""));
                function.put("arguments", functionNode.path("arguments").asText("{}"));
                toolCall.put("function", function);
                toolCalls.add(toolCall);
            }
            message.put("tool_calls", toolCalls);
        }
        return message;
    }

    private Object normalizeMessageContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return null;
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray() || contentNode.isObject()) {
            return objectMapper.convertValue(contentNode, new TypeReference<Object>() {});
        }
        return contentNode.asText();
    }

    private boolean isRetryable(Exception ex) {
        if (ex instanceof HttpStatusCodeException) {
            HttpStatus status = ((HttpStatusCodeException) ex).getStatusCode();
            return status == HttpStatus.TOO_MANY_REQUESTS
                    || status == HttpStatus.BAD_GATEWAY
                    || status == HttpStatus.SERVICE_UNAVAILABLE
                    || status == HttpStatus.GATEWAY_TIMEOUT;
        }
        if (ex instanceof ResourceAccessException) {
            return true;
        }
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("timed out")
                || lower.contains("timeout")
                || lower.contains("connection reset")
                || lower.contains("reset")
                || lower.contains("all channels failed")
                || lower.contains("overloaded");
    }

    private void sleep(int attempt) {
        long delay = llmProperties.getInitialBackoffMillis();
        if (delay <= 0) {
            delay = 500L;
        }
        for (int i = 1; i < attempt; i++) {
            delay = Math.min(delay * 2, Math.max(delay, llmProperties.getMaxBackoffMillis()));
        }
        delay = Math.min(delay, llmProperties.getMaxBackoffMillis() <= 0 ? delay : llmProperties.getMaxBackoffMillis());
        try {
            Thread.sleep(delay);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private LlmResponse fallback(String prompt) {
        List<LlmToolCall> calls = new ArrayList<LlmToolCall>();
        try {
            if (prompt != null) {
                String lower = prompt.toLowerCase();
                if (lower.contains("readme") || lower.contains("读取") || lower.contains("read")) {
                    calls.add(new LlmToolCall("fallback-readme", "read_file", "{\"path\":\"README.md\"}"));
                    return new LlmResponse(buildAssistantToolCallJson(calls), null, calls, 0L, 0L);
                }
                if (lower.contains("list") || lower.contains("文件") || lower.contains("结构")) {
                    calls.add(new LlmToolCall("fallback-list", "list_files", "{\"path\":\".\"}"));
                    return new LlmResponse(buildAssistantToolCallJson(calls), null, calls, 0L, 0L);
                }
            }
        } catch (Exception ignored) {
        }
        return new LlmResponse(buildAssistantFinalJson("[fallback] Missing CODE_AGENT_API_KEY. Prompt=" + prompt), "[fallback] Missing CODE_AGENT_API_KEY. Prompt=" + prompt, calls, 0L, 0L);
    }

    private String buildAssistantToolCallJson(List<LlmToolCall> calls) throws Exception {
        Map<String, Object> message = new LinkedHashMap<String, Object>();
        message.put("role", "assistant");
        message.put("content", null);
        List<Map<String, Object>> toolCallNodes = new ArrayList<Map<String, Object>>();
        for (LlmToolCall call : calls) {
            Map<String, Object> function = new LinkedHashMap<String, Object>();
            function.put("name", call.getName());
            function.put("arguments", call.getArgumentsJson());
            Map<String, Object> node = new LinkedHashMap<String, Object>();
            node.put("id", call.getId());
            node.put("type", "function");
            node.put("function", function);
            toolCallNodes.add(node);
        }
        message.put("tool_calls", toolCallNodes);
        return objectMapper.writeValueAsString(message);
    }

    private String buildAssistantFinalJson(String content) {
        return "{\"role\":\"assistant\",\"content\":\"" + content.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }
}
