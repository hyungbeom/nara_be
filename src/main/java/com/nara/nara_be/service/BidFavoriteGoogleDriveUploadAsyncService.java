package com.nara.nara_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidFavoriteGoogleDriveUploadAsyncService {

    private final BidFavoriteAttachmentService bidFavoriteAttachmentService;

    @Async("googleDriveUploadExecutor")
    public void upload(Long attachmentSeq, Long userSeq) {
        try {
            bidFavoriteAttachmentService.uploadToGoogleDrive(attachmentSeq, userSeq);
        } catch (RuntimeException ex) {
            log.error("구글 드라이브 비동기 업로드 실패: attachmentSeq={}", attachmentSeq, ex);
        }
    }
}
