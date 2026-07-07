package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotebookLMProjectSourceItemResponse {

    private final String id;
    private final String title;
    private final String type;
    private final boolean checked;
    private final String category;
}
