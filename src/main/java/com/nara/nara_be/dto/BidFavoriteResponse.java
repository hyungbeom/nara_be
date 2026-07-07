package com.nara.nara_be.dto;

import com.nara.nara_be.domain.BidFavorite;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
    private final LocalDateTime createdAt;

    public static BidFavoriteResponse from(BidFavorite favorite) {
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
                .createdAt(favorite.getCreatedAt())
                .build();
    }
}
