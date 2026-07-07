package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BidFavoriteAttachmentResponse {

    private final Long attachmentSeq;
    private final String fileName;
    private final String originalFileName;
    private final String contentType;
    private final Long fileSize;
    private final boolean convertedFromHwpx;
    private final String googleDriveFileId;
}
