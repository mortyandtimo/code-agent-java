package com.silvershuo.codeagent.domain.llm.service;

import com.silvershuo.codeagent.domain.llm.model.LlmResponse;

public interface LlmGateway {

    LlmResponse complete(String prompt, String messageHistoryJson, String workingDirectory);
}
