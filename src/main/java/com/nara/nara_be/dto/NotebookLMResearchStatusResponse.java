package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotebookLMResearchStatusResponse {

    private final boolean authenticated;
    private final String projectName;
    private final String notebookId;
    private final String status;
    private final String query;
    private final int sourcesCount;
    private final String message;
}
