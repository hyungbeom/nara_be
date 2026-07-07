package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NotebookLMCreateProjectResponse {

    private final String notebookId;
    private final String projectName;
    private final int addedSourceCount;
    private final List<String> failedFiles;
    private final boolean deepResearchStarted;
    private final String deepResearchQuery;
}
