package com.nara.nara_be.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class BidFavoriteCheckResponse {

    private final boolean favorited;
}
