package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotebookLMRunStepResultResponse {

    private final String step;
    private final String projectName;
    private final String notebookId;
    private final String prompt;
    private final String answer;
}
