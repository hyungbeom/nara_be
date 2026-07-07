package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class NotebookLMProjectCheckResponse {

    private final boolean authenticated;
    private final Map<String, Boolean> matches;
}
