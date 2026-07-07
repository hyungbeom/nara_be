package com.nara.nara_be.service.impl;

import com.nara.nara_be.client.G2bApiClient;
import com.nara.nara_be.client.dto.G2bAttachment;
import com.nara.nara_be.client.dto.G2bBidDetail;
import com.nara.nara_be.client.dto.G2bBidItem;
import com.nara.nara_be.client.dto.G2bBidSearchResult;
import com.nara.nara_be.config.G2bApiProperties;
import com.nara.nara_be.dto.BidAttachmentResponse;
import com.nara.nara_be.dto.BidDetailResponse;
import com.nara.nara_be.dto.BidSearchPageResponse;
import com.nara.nara_be.dto.BidSearchRequest;
import com.nara.nara_be.dto.BidSearchResponse;
import com.nara.nara_be.dto.ResolvedBidSearch;
import com.nara.nara_be.service.BidSearchParamResolver;
import com.nara.nara_be.service.BidService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BidServiceImpl implements BidService {

    private static final int CACHE_MAX_SIZE = 50;

    private final G2bApiClient g2bApiClient;
    private final G2bApiProperties g2bApiProperties;
    private final BidSearchParamResolver bidSearchParamResolver;
    private final ConcurrentHashMap<String, CachedSearch> searchCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedTotal> totalCountCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedDetail> detailCache = new ConcurrentHashMap<>();
    private final Set<String> pendingTotalCounts = ConcurrentHashMap.newKeySet();
    private final ExecutorService countExecutor = Executors.newFixedThreadPool(2);

    @PreDestroy
    void shutdownCountExecutor() {
        countExecutor.shutdownNow();
    }

    @Override
    public BidSearchPageResponse search(BidSearchRequest request) {
        ResolvedBidSearch params = bidSearchParamResolver.resolve(request);

        String cacheKey = buildCacheKey(params);
        CachedSearch cached = searchCache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            return cached.results();
        }
        if (cached != null) {
            searchCache.remove(cacheKey);
        }

        int pageSize = Math.min(params.getPageSize(), g2bApiProperties.getMaxPageSize());

        boolean multiRange = g2bApiClient.isMultiRangeSearch(params.getStartDate(), params.getEndDate());
        boolean postFilter = g2bApiClient.requiresClientSidePostFilter(
                params.getContractMethod(),
                params.isExcludeClosedBids()
        );
        boolean useSeparateCount = multiRange || postFilter;
        String totalCacheKey = buildTotalCacheKey(params);

        int totalCount;
        G2bBidSearchResult searchResult;
        boolean countApproximate = false;

        if (useSeparateCount) {
            CachedTotal cachedTotal = totalCountCache.get(totalCacheKey);
            if (cachedTotal != null && cachedTotal.isValid()) {
                totalCount = cachedTotal.totalCount();
                searchResult = fetchSearchResultSlice(params, pageSize);
            } else {
                if (cachedTotal != null) {
                    totalCountCache.remove(totalCacheKey);
                }
                searchResult = fetchSearchResultSlice(params, pageSize);
                totalCount = g2bApiClient.fetchApiRawTotalCount(
                        params.getBidName(),
                        params.getBidNo(),
                        params.getIndustry(),
                        params.getIndustryCode(),
                        params.getContractMethod(),
                        params.getStartDate(),
                        params.getEndDate(),
                        params.getMinPrice(),
                        params.getMaxPrice(),
                        params.getDateQueryType().getInqryDiv()
                );
                countApproximate = true;
                scheduleAccurateTotalCount(params, totalCacheKey);
            }
        } else {
            searchResult = fetchSearchResult(params, pageSize);
            totalCount = searchResult.getTotalCount();
            putTotalCountCache(totalCacheKey, totalCount);
        }

        List<BidSearchResponse> items = searchResult.getItems().stream()
                .map(this::toResponse)
                .collect(Collectors.toMap(
                        this::searchItemKey,
                        Function.identity(),
                        (existing, duplicate) -> existing,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();

        BidSearchPageResponse results = BidSearchPageResponse.builder()
                .items(items)
                .totalCount(totalCount)
                .fetchedCount(items.size())
                .pageNo(searchResult.getPageNo())
                .pageSize(searchResult.getPageSize())
                .truncated(totalCount > searchResult.getPageNo() * searchResult.getPageSize())
                .countApproximate(countApproximate)
                .build();

        if (!countApproximate) {
            putCache(cacheKey, results);
        }
        return results;
    }

    private void scheduleAccurateTotalCount(ResolvedBidSearch params, String totalCacheKey) {
        if (!pendingTotalCounts.add(totalCacheKey)) {
            return;
        }
        countExecutor.execute(() -> {
            try {
                int accurate = g2bApiClient.countBids(
                        params.getBidName(),
                        params.getBidNo(),
                        params.getIndustry(),
                        params.getIndustryCode(),
                        params.getContractMethod(),
                        params.getStartDate(),
                        params.getEndDate(),
                        params.getMinPrice(),
                        params.getMaxPrice(),
                        params.isExcludeClosedBids(),
                        params.getDateQueryType().getInqryDiv()
                );
                putTotalCountCache(totalCacheKey, accurate);
                invalidateSearchCacheForTotalKey(totalCacheKey);
            } finally {
                pendingTotalCounts.remove(totalCacheKey);
            }
        });
    }

    private void invalidateSearchCacheForTotalKey(String totalCacheKey) {
        searchCache.keySet().removeIf(key -> key.startsWith(totalCacheKey + "|"));
    }

    private G2bBidSearchResult fetchSearchResult(ResolvedBidSearch params, int pageSize) {
        return g2bApiClient.searchBids(
                params.getBidName(),
                params.getBidNo(),
                params.getIndustry(),
                params.getIndustryCode(),
                params.getContractMethod(),
                params.getStartDate(),
                params.getEndDate(),
                params.getMinPrice(),
                params.getMaxPrice(),
                params.isExcludeClosedBids(),
                params.getDateQueryType().getInqryDiv(),
                params.getPageNo(),
                pageSize
        );
    }

    private G2bBidSearchResult fetchSearchResultSlice(ResolvedBidSearch params, int pageSize) {
        return g2bApiClient.searchFilteredPageSlice(
                params.getBidName(),
                params.getBidNo(),
                params.getIndustry(),
                params.getIndustryCode(),
                params.getContractMethod(),
                params.getStartDate(),
                params.getEndDate(),
                params.getMinPrice(),
                params.getMaxPrice(),
                params.isExcludeClosedBids(),
                params.getDateQueryType().getInqryDiv(),
                params.getPageNo(),
                pageSize
        );
    }

    private void putTotalCountCache(String totalCacheKey, int totalCount) {
        Duration ttl = Duration.ofMinutes(g2bApiProperties.getSearchCacheMinutes());
        totalCountCache.put(totalCacheKey, new CachedTotal(totalCount, Instant.now().plus(ttl)));
    }

    private void putCache(String cacheKey, BidSearchPageResponse results) {
        if (searchCache.size() >= CACHE_MAX_SIZE) {
            searchCache.entrySet().removeIf(entry -> !entry.getValue().isValid());
        }
        Duration ttl = Duration.ofMinutes(g2bApiProperties.getSearchCacheMinutes());
        searchCache.put(cacheKey, new CachedSearch(results, Instant.now().plus(ttl)));
    }

    private String buildTotalCacheKey(ResolvedBidSearch params) {
        return String.join("|",
                nullToEmpty(params.getBidName()),
                nullToEmpty(params.getBidNo()),
                nullToEmpty(params.getIndustry()),
                nullToEmpty(params.getIndustryCode()),
                nullToEmpty(params.getContractMethod()),
                params.getStartDate().toString(),
                params.getEndDate().toString(),
                params.getDateQueryType().name(),
                String.valueOf(params.getMinPrice()),
                String.valueOf(params.getMaxPrice()),
                String.valueOf(params.isExcludeClosedBids())
        );
    }

    private String buildCacheKey(ResolvedBidSearch params) {
        return buildTotalCacheKey(params) + "|"
                + params.getPageNo() + "|"
                + params.getPageSize();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record CachedSearch(BidSearchPageResponse results, Instant expiresAt) {
        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    private record CachedTotal(int totalCount, Instant expiresAt) {
        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    @Override
    public BidDetailResponse getDetail(
            String bidNo,
            String bidOrd,
            LocalDate announceDate,
            String industryCode,
            String industryName
    ) {
        String cacheKey = buildDetailCacheKey(bidNo, bidOrd, announceDate, industryCode, industryName);
        CachedDetail cached = detailCache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            return cached.response();
        }
        if (cached != null) {
            detailCache.remove(cacheKey);
        }

        G2bBidDetail detail = g2bApiClient.getBidDetail(
                bidNo,
                bidOrd,
                announceDate,
                industryCode,
                industryName
        );
        BidDetailResponse response = toDetailResponse(detail);
        putDetailCache(cacheKey, response);
        return response;
    }

    private BidDetailResponse toDetailResponse(G2bBidDetail detail) {
        G2bBidItem item = detail.getItem();

        return BidDetailResponse.builder()
                .bidNo(item.getBidNtceNo())
                .bidName(item.getBidNtceNm())
                .industry(buildIndustryLabel(item))
                .serviceDiv(item.getSrvceDivNm())
                .contractMethod(item.getCntrctCnclsMthdNm())
                .announceDate(formatAnnounceDate(item.getBidNtceDt()))
                .estimatedPrice(calculateEstimatedPrice(item))
                .budgetAmount(parseAmount(item.getBdgtAmt()))
                .agency(item.getNtceInsttNm())
                .contactName(item.getNtceInsttOfclNm())
                .contactPhone(item.getNtceInsttOfclTelNo())
                .contactEmail(item.getNtceInsttOfclEmailAdrs())
                .regionRestriction(detail.getRegionRestriction())
                .industryRestriction(detail.getIndustryRestriction())
                .demandAgency(item.getDminsttNm())
                .detailUrl(item.getBidNtceDtlUrl())
                .bidCloseDate(formatDateTime(item.getBidClseDt()))
                .bidBeginDate(formatDateTime(item.getBidBeginDt()))
                .qualificationDeadline(formatDateTime(item.getBidQlfctRgstDt()))
                .openingDate(formatDateTime(item.getOpengDt()))
                .successBidMethod(item.getSucsfbidMthdNm())
                .bidMethod(item.getBidMethdNm())
                .detailContent(item.getBidNtceDtlCntnts())
                .attachments(detail.getAttachments().stream().map(this::toAttachmentResponse).toList())
                .build();
    }

    private void putDetailCache(String cacheKey, BidDetailResponse response) {
        if (detailCache.size() >= CACHE_MAX_SIZE) {
            detailCache.entrySet().removeIf(entry -> !entry.getValue().isValid());
        }
        Duration ttl = Duration.ofMinutes(g2bApiProperties.getSearchCacheMinutes());
        detailCache.put(cacheKey, new CachedDetail(response, Instant.now().plus(ttl)));
    }

    private String buildDetailCacheKey(
            String bidNo,
            String bidOrd,
            LocalDate announceDate,
            String industryCode,
            String industryName
    ) {
        return String.join("|",
                nullToEmpty(bidNo),
                nullToEmpty(bidOrd),
                announceDate.toString(),
                nullToEmpty(industryCode),
                nullToEmpty(industryName)
        );
    }

    private record CachedDetail(BidDetailResponse response, Instant expiresAt) {
        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    private BidAttachmentResponse toAttachmentResponse(G2bAttachment attachment) {
        return BidAttachmentResponse.builder()
                .fileName(attachment.getFileName())
                .fileUrl(attachment.getFileUrl())
                .build();
    }

    private String formatDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private BidSearchResponse toResponse(G2bBidItem item) {
        return BidSearchResponse.builder()
                .bidNo(item.getBidNtceNo())
                .bidOrd(StringUtils.hasText(item.getBidNtceOrd()) ? item.getBidNtceOrd().trim() : "0")
                .bidName(item.getBidNtceNm())
                .industry(buildIndustryLabel(item))
                .serviceDiv(item.getSrvceDivNm())
                .contractMethod(item.getCntrctCnclsMthdNm())
                .bidMethod(item.getBidMethdNm())
                .announceDate(formatAnnounceDate(item.getBidNtceDt()))
                .openingDate(formatAnnounceDate(item.getOpengDt()))
                .estimatedPrice(calculateEstimatedPrice(item))
                .agency(StringUtils.hasText(item.getNtceInsttNm()) ? item.getNtceInsttNm() : item.getDminsttNm())
                .detailUrl(item.getBidNtceDtlUrl())
                .build();
    }

    private String searchItemKey(BidSearchResponse item) {
        return item.getBidNo() + "|" + item.getBidOrd() + "|" + item.getAnnounceDate();
    }

    private String buildIndustryLabel(G2bBidItem item) {
        if (StringUtils.hasText(item.getPubPrcrmntClsfcNm())) {
            return item.getPubPrcrmntClsfcNm().trim();
        }
        if (StringUtils.hasText(item.getPubPrcrmntMidClsfcNm())) {
            return item.getPubPrcrmntMidClsfcNm().trim();
        }
        return item.getPubPrcrmntLrgClsfcNm() != null ? item.getPubPrcrmntLrgClsfcNm().trim() : "";
    }

    private String formatAnnounceDate(String bidNtceDt) {
        if (!StringUtils.hasText(bidNtceDt)) {
            return "";
        }
        return bidNtceDt.length() >= 10 ? bidNtceDt.substring(0, 10) : bidNtceDt;
    }

    private long calculateEstimatedPrice(G2bBidItem item) {
        long presmpt = parseAmount(item.getPresmptPrce());
        long vat = parseAmount(item.getVat());
        return presmpt + vat;
    }

    private long parseAmount(String value) {
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
