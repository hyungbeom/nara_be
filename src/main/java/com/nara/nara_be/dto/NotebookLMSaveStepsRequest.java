package com.nara.nara_be.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class NotebookLMSaveStepsRequest {

    private List<NotebookLMStepItemRequest> steps = new ArrayList<>();
}
