package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BidSearchResponse {

    private String bidNo;
    private String bidOrd;
    private String bidName;
    private String industry;
    private String serviceDiv;
    private String contractMethod;
    private String bidMethod;
    private String announceDate;
    private String openingDate;
    private Long estimatedPrice;
    private String agency;
    private String detailUrl;
}
