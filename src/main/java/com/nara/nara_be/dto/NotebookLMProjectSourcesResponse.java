package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NotebookLMProjectSourcesResponse {

    private final String projectName;
    private final String notebookId;
    private final List<NotebookLMProjectSourceItemResponse> sources;
}
