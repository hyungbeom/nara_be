package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoogleDriveStatusResponse {

    private final boolean available;
    private final boolean sharedDriveConfigured;
    private final String sharedDriveId;
    private final String rootFolderId;
}
