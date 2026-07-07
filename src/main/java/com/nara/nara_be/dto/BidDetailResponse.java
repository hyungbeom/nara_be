package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BidDetailResponse {

    private String bidNo;
    private String bidName;
    private String industry;
    private String serviceDiv;
    private String contractMethod;
    private String announceDate;
    private Long estimatedPrice;
    private Long budgetAmount;
    private String agency;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String regionRestriction;
    private String industryRestriction;
    private String demandAgency;
    private String detailUrl;
    private String bidCloseDate;
    private String bidBeginDate;
    private String qualificationDeadline;
    private String openingDate;
    private String successBidMethod;
    private String bidMethod;
    private String detailContent;
    private List<BidAttachmentResponse> attachments;
}
