package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotebookLMProjectSourceContentResponse {

    private final String projectName;
    private final String notebookId;
    private final String sourceId;
    private final String title;
    private final String content;
}
