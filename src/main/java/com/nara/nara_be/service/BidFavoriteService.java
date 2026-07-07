package com.nara.nara_be.service;

import com.nara.nara_be.dto.BidFavoriteCheckResponse;
import com.nara.nara_be.dto.BidFavoriteRequest;
import com.nara.nara_be.dto.BidFavoriteResponse;

import java.time.LocalDate;
import java.util.List;

public interface BidFavoriteService {

    List<BidFavoriteResponse> findAll(String userId);

    BidFavoriteCheckResponse check(String userId, String bidNo, String bidOrd, LocalDate announceDate);

    void add(String userId, BidFavoriteRequest request);

    void remove(String userId, String bidNo, String bidOrd, LocalDate announceDate);
}
