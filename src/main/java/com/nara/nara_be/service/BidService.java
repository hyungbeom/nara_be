package com.nara.nara_be.service;

import com.nara.nara_be.dto.BidDetailResponse;
import com.nara.nara_be.dto.BidSearchPageResponse;
import com.nara.nara_be.dto.BidSearchRequest;

import java.time.LocalDate;

public interface BidService {

    BidSearchPageResponse search(BidSearchRequest request);

    BidDetailResponse getDetail(
            String bidNo,
            String bidOrd,
            LocalDate announceDate,
            String industryCode,
            String industryName
    );
}
