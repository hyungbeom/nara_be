package com.nara.nara_be.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotebookLMProjectSourceRequest {

    private String driveFileId;
    private String fileName;
    private String mimeType;
}
