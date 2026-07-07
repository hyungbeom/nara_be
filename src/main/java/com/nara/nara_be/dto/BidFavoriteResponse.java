package com.nara.nara_be.dto;

import com.nara.nara_be.domain.BidFavorite;
import com.nara.nara_be.domain.BidFavoriteAttachment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class BidFavoriteResponse {

    private final Long favoriteSeq;
    private final String bidNo;
    private final String bidOrd;
    private final LocalDate announceDate;
    private final String bidName;
    private final String industry;
    private final String contractMethod;
    private final String openingDate;
    private final Long estimatedPrice;
    private final String agency;
    private final String detailUrl;
    private final String detailContent;
    private final String contactName;
    private final String contactPhone;
    private final String contactEmail;
    private final List<BidFavoriteAttachmentResponse> attachments;
    private final LocalDateTime createdAt;

    public static BidFavoriteResponse from(BidFavorite favorite, List<BidFavoriteAttachment> attachments) {
        return BidFavoriteResponse.builder()
                .favoriteSeq(favorite.getFavoriteSeq())
                .bidNo(favorite.getBidNo())
                .bidOrd(favorite.getBidOrd())
                .announceDate(favorite.getAnnounceDate())
                .bidName(favorite.getBidName())
                .industry(favorite.getIndustry())
                .contractMethod(favorite.getContractMethod())
                .openingDate(favorite.getOpeningDate())
                .estimatedPrice(favorite.getEstimatedPrice())
                .agency(favorite.getAgency())
                .detailUrl(favorite.getDetailUrl())
                .detailContent(favorite.getDetailContent())
                .contactName(favorite.getContactName())
                .contactPhone(favorite.getContactPhone())
                .contactEmail(favorite.getContactEmail())
                .attachments(attachments.stream().map(BidFavoriteResponse::toAttachmentResponse).toList())
                .createdAt(favorite.getCreatedAt())
                .build();
    }

    private static BidFavoriteAttachmentResponse toAttachmentResponse(BidFavoriteAttachment attachment) {
        return BidFavoriteAttachmentResponse.builder()
                .attachmentSeq(attachment.getAttachmentSeq())
                .fileName(attachment.getStoredFileName())
                .originalFileName(attachment.getOriginalFileName())
                .contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize())
                .convertedFromHwpx(attachment.isConvertedFromHwpx())
                .googleDriveFileId(attachment.getGoogleDriveFileId())
                .build();
    }
}
