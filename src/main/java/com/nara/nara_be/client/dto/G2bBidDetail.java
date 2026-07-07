package com.nara.nara_be.client.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class G2bBidDetail {

    private G2bBidItem item;
    private List<G2bAttachment> attachments;
    private String regionRestriction;
    private String industryRestriction;
}
