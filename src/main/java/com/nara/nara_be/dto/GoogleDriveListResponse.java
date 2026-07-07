package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GoogleDriveListResponse {

    private final String currentFolderId;
    private final String currentFolderName;
    private final String parentFolderId;
    private final List<GoogleDriveBreadcrumbItem> breadcrumbs;
    private final List<GoogleDriveFileResponse> files;
}
