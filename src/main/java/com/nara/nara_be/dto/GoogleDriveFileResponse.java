package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoogleDriveFileResponse {

    private final String id;
    private final String name;
    private final String mimeType;
    private final Long size;
    private final String modifiedTime;
    private final boolean folder;
    private final String webViewLink;
    private final String parentId;
}
