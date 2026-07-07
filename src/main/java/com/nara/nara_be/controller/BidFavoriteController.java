package com.nara.nara_be.controller;

import com.nara.nara_be.common.response.ApiResponse;
import com.nara.nara_be.dto.BidFavoriteCheckResponse;
import com.nara.nara_be.dto.BidFavoriteRequest;
import com.nara.nara_be.dto.BidFavoriteResponse;
import com.nara.nara_be.exception.BusinessException;
import com.nara.nara_be.service.BidFavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bids/favorites")
@RequiredArgsConstructor
public class BidFavoriteController {

    private final BidFavoriteService bidFavoriteService;

    @GetMapping
    public ApiResponse<List<BidFavoriteResponse>> list(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(bidFavoriteService.findAll(requireUserId(userDetails)));
    }

    @GetMapping("/check")
    public ApiResponse<BidFavoriteCheckResponse> check(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String bidNo,
            @RequestParam String bidOrd,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate announceDate
    ) {
        return ApiResponse.success(
                bidFavoriteService.check(requireUserId(userDetails), bidNo, bidOrd, announceDate)
        );
    }

    @PostMapping
    public ApiResponse<Void> add(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody BidFavoriteRequest request
    ) {
        bidFavoriteService.add(requireUserId(userDetails), request);
        return ApiResponse.success("즐겨찾기에 저장했습니다.", null);
    }

    @DeleteMapping
    public ApiResponse<Void> remove(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String bidNo,
            @RequestParam String bidOrd,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate announceDate
    ) {
        bidFavoriteService.remove(requireUserId(userDetails), bidNo, bidOrd, announceDate);
        return ApiResponse.success("즐겨찾기에서 삭제했습니다.", null);
    }

    private String requireUserId(UserDetails userDetails) {
        if (userDetails == null) {
            throw new BusinessException("인증이 필요합니다.", HttpStatus.UNAUTHORIZED);
        }
        return userDetails.getUsername();
    }
}
