package com.nara.nara_be.client.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class G2bBidSearchResult {

    private List<G2bBidItem> items;
    private int totalCount;
    private int pageNo;
    private int pageSize;
}