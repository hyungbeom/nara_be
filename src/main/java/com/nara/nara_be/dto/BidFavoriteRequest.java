package com.nara.nara_be.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class BidFavoriteRequest {

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
    private String industryCode;
    private String industryName;
}
