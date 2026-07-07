package com.nara.nara_be.service;

import com.nara.nara_be.domain.BidFavoriteAttachment;
import com.nara.nara_be.dto.BidAttachmentResponse;
import com.nara.nara_be.dto.BidFavoriteAttachmentResponse;
import com.nara.nara_be.dto.GoogleDriveUploadAttachmentResponse;
import org.springframework.core.io.Resource;

import java.util.List;

public interface BidFavoriteAttachmentService {

    void saveAttachments(Long favoriteSeq, List<BidAttachmentResponse> attachments);

    void deleteAttachments(Long favoriteSeq);

    Resource loadAttachmentFile(Long attachmentSeq, Long userSeq);

    BidFavoriteAttachment findAttachmentForUser(Long attachmentSeq, Long userSeq);

    BidFavoriteAttachmentResponse convertToPdf(Long attachmentSeq, Long userSeq);

    GoogleDriveUploadAttachmentResponse uploadToGoogleDrive(Long attachmentSeq, Long userSeq);

    void enqueueGoogleDriveUpload(Long attachmentSeq, Long userSeq);
}
