package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BidSearchPageResponse {

    private List<BidSearchResponse> items;
    private int totalCount;
    private int fetchedCount;
    private int pageNo;
    private int pageSize;
    private boolean truncated;
    private boolean countApproximate;
}
