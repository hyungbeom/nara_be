package com.nara.nara_be.controller;

import com.nara.nara_be.common.response.ApiResponse;
import com.nara.nara_be.dto.BidDetailResponse;
import com.nara.nara_be.dto.BidSearchPageResponse;
import com.nara.nara_be.dto.BidSearchRequest;
import com.nara.nara_be.service.BidService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/bids")
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;

    @PostMapping("/search")
    public ApiResponse<BidSearchPageResponse> search(@RequestBody BidSearchRequest request) {
        return ApiResponse.success(bidService.search(request));
    }

    @GetMapping("/{bidNo}")
    public ApiResponse<BidDetailResponse> detail(
            @PathVariable String bidNo,
            @RequestParam String bidOrd,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate announceDate,
            @RequestParam(required = false) String industryCode,
            @RequestParam(required = false) String industryName
    ) {
        return ApiResponse.success(bidService.getDetail(bidNo, bidOrd, announceDate, industryCode, industryName));
    }
}
