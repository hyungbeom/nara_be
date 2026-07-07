package com.nara.nara_be.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotebookLMStepItemRequest {

    private String id;
    private String label;
    private String prompt;
}
