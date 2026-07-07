package com.nara.nara_be.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BidFavorite {

    private Long favoriteSeq;
    private Long userSeq;
    private String bidNo;
    private String bidOrd;
    private LocalDate announceDate;
    private String bidName;
    private String industry;
    private String contractMethod;
    private String openingDate;
    private Long estimatedPrice;
    private String agency;
    private String detailUrl;
    private LocalDateTime createdAt;

    @Builder
    public BidFavorite(
            Long userSeq,
            String bidNo,
            String bidOrd,
            LocalDate announceDate,
            String bidName,
            String industry,
            String contractMethod,
            String openingDate,
            Long estimatedPrice,
            String agency,
            String detailUrl
    ) {
        this.userSeq = userSeq;
        this.bidNo = bidNo;
        this.bidOrd = bidOrd;
        this.announceDate = announceDate;
        this.bidName = bidName;
        this.industry = industry;
        this.contractMethod = contractMethod;
        this.openingDate = openingDate;
        this.estimatedPrice = estimatedPrice;
        this.agency = agency;
        this.detailUrl = detailUrl;
    }
}
