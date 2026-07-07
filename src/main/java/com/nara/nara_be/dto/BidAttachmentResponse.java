package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BidAttachmentResponse {

    private String fileName;
    private String fileUrl;
}
