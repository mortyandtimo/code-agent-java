package com.silvershuo.codeagent.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "code-agent.llm")
public class LlmProperties {

    private String baseUrl;
    private String apiKey;
    private String model;
    private int timeoutSeconds;
    private int maxRetries;
    private long initialBackoffMillis;
    private long maxBackoffMillis;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public long getInitialBackoffMillis() { return initialBackoffMillis; }
    public void setInitialBackoffMillis(long initialBackoffMillis) { this.initialBackoffMillis = initialBackoffMillis; }
    public long getMaxBackoffMillis() { return maxBackoffMillis; }
    public void setMaxBackoffMillis(long maxBackoffMillis) { this.maxBackoffMillis = maxBackoffMillis; }
}
