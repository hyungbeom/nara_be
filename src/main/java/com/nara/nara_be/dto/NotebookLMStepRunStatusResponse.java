package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotebookLMStepRunStatusResponse {

    private final String projectName;
    private final String notebookId;
    private final String step;
    private final String status;
    private final String message;
    private final String answer;
}
