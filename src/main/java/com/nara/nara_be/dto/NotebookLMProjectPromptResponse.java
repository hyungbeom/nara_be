package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotebookLMProjectPromptResponse {

    private final int turn;
    private final String question;
    private final String answer;
}
