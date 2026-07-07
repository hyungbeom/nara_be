package com.nara.nara_be.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class NotebookLMUpdateSourceSelectionRequest {

    private String projectName;
    private List<String> selectedSourceIds;
}
