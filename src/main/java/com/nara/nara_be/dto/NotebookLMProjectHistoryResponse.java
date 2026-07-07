package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NotebookLMProjectHistoryResponse {

    private final boolean authenticated;
    private final String projectName;
    private final String notebookId;
    private final String conversationId;
    private final int count;
    private final List<NotebookLMProjectPromptResponse> prompts;
}
