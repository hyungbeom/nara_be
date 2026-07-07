package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class ResolvedBidSearch {

    public static final String DEFAULT_INDUSTRY_NAME = "소프트웨어사업자(컴퓨터관련서비스업)";
    public static final String DEFAULT_INDUSTRY_CODE = "1468";
    public static final String CONTRACT_METHOD_ALL = "전체";

    private final String bidName;
    private final String bidNo;
    private final String industry;
    private final String industryCode;
    private final String contractMethod;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BidDateQueryType dateQueryType;
    private final Long minPrice;
    private final Long maxPrice;
    private final int pageNo;
    private final int pageSize;
    private final boolean excludeClosedBids;
}
