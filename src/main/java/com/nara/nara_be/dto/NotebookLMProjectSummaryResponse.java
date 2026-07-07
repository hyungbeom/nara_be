package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotebookLMProjectSummaryResponse {

    private final boolean authenticated;
    private final String projectName;
    private final String notebookId;
    private final boolean showSummary;
    private final String summary;
    private final String message;
}
