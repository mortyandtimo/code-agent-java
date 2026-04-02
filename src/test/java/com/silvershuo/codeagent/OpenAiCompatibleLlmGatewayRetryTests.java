package com.silvershuo.codeagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvershuo.codeagent.domain.llm.model.LlmResponse;
import com.silvershuo.codeagent.infrastructure.config.LlmProperties;
import com.silvershuo.codeagent.infrastructure.llm.OpenAiCompatibleLlmGateway;
import com.silvershuo.codeagent.infrastructure.prompt.SystemPromptBuilder;
import com.silvershuo.codeagent.infrastructure.tool.LocalToolExecutor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleLlmGatewayRetryTests {

    @Test
    void gatewayShouldRetryAndSucceed() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(ExpectedCount.times(2), requestTo("http://example.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("{\"code\":502,\"message\":\"all channels failed\"}").contentType(MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), requestTo("http://example.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"ok\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}", MediaType.APPLICATION_JSON));

        LlmProperties properties = new LlmProperties();
        properties.setBaseUrl("http://example.com/v1");
        properties.setApiKey("test-key");
        properties.setModel("gpt-5.4");
        properties.setMaxRetries(3);
        properties.setInitialBackoffMillis(1L);
        properties.setMaxBackoffMillis(2L);

        OpenAiCompatibleLlmGateway gateway = new OpenAiCompatibleLlmGateway(restTemplate, new ObjectMapper(), properties, new LocalToolExecutor(), new SystemPromptBuilder());
        LlmResponse response = gateway.complete("test", "[]", ".");
        Assertions.assertEquals("ok", response.getFinalAnswer());
        server.verify();
    }
}
