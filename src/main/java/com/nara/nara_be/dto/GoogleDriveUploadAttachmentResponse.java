package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoogleDriveUploadAttachmentResponse {

    private final String folderId;
    private final String folderName;
    private final GoogleDriveFileResponse file;
}
