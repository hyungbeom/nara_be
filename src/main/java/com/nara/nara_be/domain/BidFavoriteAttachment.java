package com.nara.nara_be.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BidFavoriteAttachment {

    private Long attachmentSeq;
    private Long favoriteSeq;
    private String originalFileName;
    private String storedFileName;
    private String originalUrl;
    private String contentType;
    private Long fileSize;
    private boolean convertedFromHwpx;
    private String storagePath;
    private String googleDriveFileId;
    private LocalDateTime createdAt;

    @Builder
    public BidFavoriteAttachment(
            Long favoriteSeq,
            String originalFileName,
            String storedFileName,
            String originalUrl,
            String contentType,
            Long fileSize,
            boolean convertedFromHwpx,
            String storagePath
    ) {
        this.favoriteSeq = favoriteSeq;
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.originalUrl = originalUrl;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.convertedFromHwpx = convertedFromHwpx;
        this.storagePath = storagePath;
    }
}
