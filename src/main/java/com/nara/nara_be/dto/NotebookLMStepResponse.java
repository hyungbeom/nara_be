package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotebookLMStepResponse {

    private final String id;
    private final String label;
    private final String prompt;
}
