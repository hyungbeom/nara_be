package com.nara.nara_be.client;

import com.nara.nara_be.client.dto.G2bAttachment;
import com.nara.nara_be.client.dto.G2bBidSearchResult;
import com.nara.nara_be.client.dto.G2bBidDetail;
import com.nara.nara_be.client.dto.G2bBidItem;
import com.nara.nara_be.config.G2bApiProperties;
import com.nara.nara_be.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
@RequiredArgsConstructor
@Slf4j
public class G2bApiClient {

    private static final DateTimeFormatter API_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter BID_CLOSE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int COUNT_ONLY_ROWS = 1;
    private static final String EORDER_ATCH_OPERATION = "getBidPblancListInfoEorderAtchFileInfo";
    private static final List<String> PRTCPT_PSBL_RGN_OPERATIONS = List.of(
            "getBidPblancListInfoPrtcptPsblRgn"
    );
    private static final String SERVC_DETAIL_OPERATION = "getBidPblancListInfoServc";
    private static final List<String> BID_PRC_PSBL_INDSTRYTY_OPERATIONS = List.of(
            "getBidPblancListInfoLicnsLmt",
            "getBidPblancListInfoBidPrcPsblIndstrytyServc",
            "getBidPblancListInfoBidPrcPsblIndstryty"
    );

    /** 나라장터 용역 API(getBidPblancListInfoServcPPSSrch) 응답 srvceDivNm 허용값 */
    private static final Set<String> ALLOWED_SERVICE_DIVISIONS = Set.of("일반용역", "기술용역");

    private final RestClient g2bRestClient;
    private final G2bApiProperties properties;
    private final JsonMapper jsonMapper;

    public boolean isMultiRangeSearch(LocalDate startDate, LocalDate endDate) {
        return splitDateRange(startDate, endDate, properties.getMaxDaysPerRequest()).size() > 1;
    }

    /**
     * 나라장터는 count 전용 API가 없어 numOfRows=1 로 totalCount 만 조회한다.
     * 30일 초과(다구간) 검색은 구간별 count 를 병렬 조회해 합산한다.
     */
    public int countBids(
            String bidName,
            String bidNo,
            String industry,
            String industryCode,
            String contractMethod,
            LocalDate startDate,
            LocalDate endDate,
            Long minPrice,
            Long maxPrice,
            boolean excludeClosedBids,
            int inqryDiv
    ) {
        if (requiresClientSidePostFilter(contractMethod, excludeClosedBids)) {
            return countFilteredBids(
                    bidName,
                    bidNo,
                    industry,
                    industryCode,
                    contractMethod,
                    startDate,
                    endDate,
                    minPrice,
                    maxPrice,
                    excludeClosedBids,
                    inqryDiv
            );
        }

        return fetchApiRawTotalCount(
                bidName,
                bidNo,
                industry,
                industryCode,
                contractMethod,
                startDate,
                endDate,
                minPrice,
                maxPrice,
                inqryDiv
        );
    }

    /** Open API totalCount 합산(필터 전). 빠른 건수 추정용. */
    public int fetchApiRawTotalCount(
            String bidName,
            String bidNo,
            String industry,
            String industryCode,
            String contractMethod,
            LocalDate startDate,
            LocalDate endDate,
            Long minPrice,
            Long maxPrice,
            int inqryDiv
    ) {
        List<DateRange> ranges = splitDateRange(startDate, endDate, properties.getMaxDaysPerRequest());
        if (ranges.size() == 1) {
            JsonNode body = callApi(
                    bidName,
                    bidNo,
                    industry,
                    industryCode,
                    contractMethod,
                    ranges.get(0),
                    minPrice,
                    maxPrice,
                    inqryDiv,
                    1,
                    COUNT_ONLY_ROWS
            );
            return readInt(body.path("totalCount"));
        }

        int parallelism = Math.min(properties.getMaxParallelRequests(), ranges.size());
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (DateRange range : ranges) {
                futures.add(executor.submit(() -> {
                    JsonNode body = callApi(
                            bidName,
                            bidNo,
                            industry,
                            industryCode,
                            contractMethod,
                            range,
                            minPrice,
                            maxPrice,
                            inqryDiv,
                            1,
                            COUNT_ONLY_ROWS
                    );
                    return readInt(body.path("totalCount"));
                }));
            }

            int totalCount = 0;
            for (Future<Integer> future : futures) {
                totalCount += future.get();
            }
            return totalCount;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("건수 조회가 중단되었습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("건수 조회 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            executor.shutdownNow();
        }
    }

    public G2bBidSearchResult searchBids(
            String bidName,
            String bidNo,
            String industry,
            String industryCode,
            String contractMethod,
            LocalDate startDate,
            LocalDate endDate,
            Long minPrice,
            Long maxPrice,
            boolean excludeClosedBids,
            int inqryDiv,
            int pageNo,
            int pageSize
    ) {
        int resolvedPageNo = Math.max(pageNo, 1);
        int resolvedPageSize = Math.min(Math.max(pageSize, 1), properties.getMaxPageSize());
        List<DateRange> ranges = splitDateRange(startDate, endDate, properties.getMaxDaysPerRequest());

        if (ranges.size() == 1 && !requiresClientSidePostFilter(contractMethod, excludeClosedBids)) {
            return searchSingleRangePage(
                    bidName,
                    bidNo,
                    industry,
                    industryCode,
                    contractMethod,
                    ranges.get(0),
                    minPrice,
                    maxPrice,
                    excludeClosedBids,
                    inqryDiv,
                    resolvedPageNo,
                    resolvedPageSize
            );
        }

        return fetchFilteredPage(
                bidName,
                bidNo,
                industry,
                industryCode,
                contractMethod,
                ranges,
                minPrice,
                maxPrice,
                excludeClosedBids,
                inqryDiv,
                resolvedPageNo,
                resolvedPageSize,
                true
        );
    }

    /**
     * totalCount 가 이미 알려진 경우 해당 페이지 items 만 순차 조회한다.
     */
    public G2bBidSearchResult searchFilteredPageSlice(
            String bidName,
            String bidNo,
            String industry,
            String industryCode,
            String contractMethod,
            LocalDate startDate,
            LocalDate endDate,
            Long minPrice,
            Long maxPrice,
            boolean excludeClosedBids,
            int inqryDiv,
            int pageNo,
            int pageSize
    ) {
        int resolvedPageNo = Math.max(pageNo, 1);
        int resolvedPageSize = Math.min(Math.max(pageSize, 1), properties.getMaxPageSize());
        List<DateRange> ranges = splitDateRange(startDate, endDate, properties.getMaxDaysPerRequest());

        return fetchFilteredPage(
                bidName,
                bidNo,
                industry,
                industryCode,
                contractMethod,
                ranges,
                minPrice,
                maxPrice,
                excludeClosedBids,
                inqryDiv,
                resolvedPageNo,
                resolvedPageSize,
                false
        );
    }

    public boolean requiresClientSideContractFilter(String contractMethod) {
        return StringUtils.hasText(contractMethod) && !"전체".equals(contractMethod.trim());
    }

    /**
     * 일반용역·기술용역은 별도 Open API가 없고 용역 API 응답의 srvceDivNm 으로 구분한다.
     * API totalCount 는 필터 전 건수이므로 서버에서 2차 필터링한다.
     */
    public boolean requiresClientSideServiceDivFilter() {
        return true;
    }

    public boolean requiresClientSidePostFilter(String contractMethod, boolean excludeClosedBids) {
        return requiresClientSideServiceDivFilter()
                || requiresClientSideContractFilter(contractMethod)
                || excludeClosedBids;
    }

    private int countFilteredBids(
            String bidName,
            String bidNo,
            String industry,
            String industryCode,
            String contractMethod,
            LocalDate startDate,
            LocalDate endDate,
            Long minPrice,
            Long maxPrice,
            boolean excludeClosedBids,
            int inqryDiv
    ) {
        return scanFilteredBids(
                bidName,
                bidNo,
                industry,
                industryCode,
                contractMethod,
                startDate,
                endDate,
                minPrice,
                maxPrice,
                excludeClosedBids,
                inqryDiv,
                1,
                1
        ).getTotalCount();
    }

    /**
     * 단일 구간 + 계약방법 전체: G2B pageNo 1회 호출로 totalCount + 해당 페이지 items 를 함께 반환한다.
     */
    private G2bBidSearchResult searchSingleRangePage(
            String bidName,
            String bidNo,
            String industry,
            String industryCode,
            String contractMethod,
            DateRange range,
            Long minPrice,
            Long maxPrice,
            boolean excludeClosedBids,
            int inqryDiv,
            int pageNo,
            int pageSize
    ) {
        JsonNode body = callApi(
                bidName,
                bidNo,
                industry,
                industryCode,
                contractMethod,
                range,
                minPrice,
                maxPrice,
                inqryDiv,
                pageNo,
                pageSize
        );
        List<G2bBidItem> items = distinctItems(parseItems(body.path("items")).stream()
                .filter(item -> matchesSearchFilters(
                        item,
                        industry,
                        industryCode,
                        contractMethod,
                        excludeClosedBids
                ))
                .toList());
        int totalCount = readInt(body.path("totalCount"));

        return G2bBidSearchResult.builder()
                .items(items)
                .totalCount(totalCount)
                .pageNo(pageNo)
                .pageSize(pageSize)
                .build();
    }

    private G2bBidSearchResult fetchFilteredPage(
            String bidName,
            String bidNo,
            String industry,
            String industryCode,
            String contractMethod,
            List<DateRange> ranges,
            Long minPrice,
            Long maxPrice,
            boolean excludeClosedBids,
            int inqryDiv,
            int pageNo,
            int pageSize,
            boolean includeTotalCount
    ) {
        if (includeTotalCount) {
            return scanFilteredBids(
                    bidName,
                    bidNo,
                    industry,
                    industryCode,
                    contractMethod,
                    ranges.get(0).start().toLocalDate(),
                    ranges.get(ranges.size() - 1).end().toLocalDate(),
                    minPrice,
                    maxPrice,
                    excludeClosedBids,
                    inqryDiv,
                    pageNo,
                    pageSize
            );
        }

        return fetchFilteredPageSlice(
                bidName,
                bidNo,
                industry,
                industryCode,
                contractMethod,
                ranges,
                minPrice,
                maxPrice,
                excludeClosedBids,
                inqryDiv,
                pageNo,
                pageSize
        );
    }

    private G2bBidSearchResult fetchFilteredPageSlice(
            String bidName,
            String bidNo,
            String industry,
            String industryCode,
            String contractMethod,
            List<DateRange> ranges,
            Long minPrice,
            Long maxPrice,
            boolean excludeClosedBids,
            int inqryDiv,
            int pageNo,
            int pageSize
    ) {
        LocalDate startDate = ranges.get(0).start().toLocalDate();
        LocalDate endDate = ranges.get(ranges.size() - 1).end().toLocalDate();
        List<G2bBidItem> matchedItems = collectFilteredItems(
                bidName,
                bidNo,
                industry,
                industryCode,
                contractMethod,
                startDate,
                endDate,
                minPrice,
                maxPrice,
                excludeClosedBids,
                inqryDiv
        );

        int skip = (pageNo - 1) * pageSize;
        List<G2bBidItem> pageItems = matchedItems.stream()
                .skip(skip)
                .limit(pageSize)
                .toList();

        return G2bBidSearchResult.builder()
                .items(pageItems)
                .totalCount(0)
                .pageNo(pageNo)
                .pageSize(pageSize)
                .build();
    }

    private G2bBidSearchResult scanFilteredBids(
            String bidName,
            String bidNo,
            String industry,
            String industryCode,
            String contractMethod,
            LocalDate startDate,
            LocalDate endDate,
            Long minPrice,
            Long maxPrice,
            boolean excludeClosedBids,
            int inqryDiv,
            int pageNo,
            int pageSize
    ) {
        List<G2bBidItem> matchedItems = collectFilteredItems(
                bidName,
                bidNo,
                industry,
                industryCode,
                contractMethod,
                startDate,
                endDate,
                minPrice,
                maxPrice,
                excludeClosedBids,
                inqryDiv
        );

        int totalCount = matchedItems.size();
        int skip = (pageNo - 1) * pageSize;
        List<G2bBidItem> pageItems = matchedItems.stream()
                .skip(skip)
                .limit(pageSize)
                .toList();

        return G2bBidSearchResult.builder()
                .items(pageItems)
                .totalCount(totalCount)
                .pageNo(pageNo)
                .pageSize(pageSize)
                .build();
    }

    private List<G2bBidItem> collectFilteredItems(
            String bidName,
            String bidNo,
            String industry,
            String industryCode,
            String contractMethod,
            LocalDate startDate,
            LocalDate endDate,
            Long minPrice,
            Long maxPrice,
            boolean excludeClosedBids,
            int inqryDiv
    ) {
        List<DateRange> ranges = splitDateRange(startDate, endDate, properties.getMaxDaysPerRequest());
        Set<String> seenKeys = new LinkedHashSet<>();
        List<G2bBidItem> matchedItems = new ArrayList<>();

        for (DateRange range : ranges) {
            for (G2bBidItem item : fetchFilteredItemsForRange(
                    bidName,
                    bidNo,
                    industry,
                    industryCode,
                    contractMethod,
                    range,
                    minPrice,
                    maxPrice,
                    excludeClosedBids,
                    inqryDiv
            )) {
                if (seenKeys.add(buildItemKey(item))) {
                    matchedItems.add(item);
                }
            }
        }

        return sortByBidNtceDtDesc(matchedItems);
    }

    private List<G2bBidItem> sortByBidNtceDtDesc(List<G2bBidItem> items) {
        return items.stream()
                .sorted(Comparator.comparing(
                        G2bBidItem::getBidNtceDt,
                        Comparator.nullsLast(String::compareTo)
                ).reversed())
                .toList();
    }

    private List<G2bBidItem> fetchFilteredItemsForRange(
            String bidName,
            String bidNo,
            String industry,
            String industryCode,
            String contractMethod,
            DateRange range,
            Long minPrice,
            Long maxPrice,
            boolean excludeClosedBids,
            int inqryDiv
    ) {
        List<G2bBidItem> items = new ArrayList<>();

        for (int g2bPage = 1; g2bPage <= properties.getMaxPages(); g2bPage++) {
            JsonNode body = callApi(
                    bidName,
                    bidNo,
                    industry,
                    industryCode,
                    contractMethod,
                    range,
                    minPrice,
                    maxPrice,
                    inqryDiv,
                    g2bPage,
                    properties.getNumOfRows()
            );

            List<G2bBidItem> pageItems = distinctItems(parseItems(body.path("items")).stream()
                    .filter(item -> matchesSearchFilters(
                            item,
                            industry,
                            industryCode,
                            contractMethod,
                            excludeClosedBids
                    ))
                    .toList());
            if (pageItems.isEmpty()) {
                break;
            }
            items.addAll(pageItems);

            int rangeTotal = readInt(body.path("totalCount"));
            if (g2bPage * properties.getNumOfRows() >= rangeTotal) {
                break;
            }
        }

        return items;
    }

    public G2bBidDetail getBidDetail(
            String bidNtceNo,
            String bidNtceOrd,
            LocalDate announceDate,
            String industryCode,
            String industryName
    ) {
        JsonNode rawItem = findBidItemNode(bidNtceNo, bidNtceOrd, announceDate, industryCode);
        JsonNode servcItem = fetchServcBidItemNode(bidNtceNo.trim(), bidNtceOrd);
        JsonNode industrySource = selectIndustryRestrictionSource(servcItem, rawItem);
        G2bBidItem item = jsonMapper.treeToValue(rawItem, G2bBidItem.class);
        String regionRestriction = resolveRegionRestriction(rawItem, bidNtceNo.trim(), bidNtceOrd);
        String industryRestriction = resolveIndustryRestriction(
                industrySource,
                bidNtceNo.trim(),
                bidNtceOrd
        );
        item.setBidNtceDtlCntnts(resolveDetailContent(item, rawItem, regionRestriction, industryRestriction));

        List<G2bAttachment> attachments = parseAttachments(rawItem);
        appendEorderAttachments(attachments, bidNtceNo.trim());

        return G2bBidDetail.builder()
                .item(item)
                .attachments(attachments)
                .regionRestriction(regionRestriction)
                .industryRestriction(industryRestriction)
                .build();
    }

    private JsonNode findBidItemNode(
            String bidNtceNo,
            String bidNtceOrd,
            LocalDate announceDate,
            String industryCode
    ) {
        String targetNo = bidNtceNo.trim();
        String targetOrd = normalizeBidOrd(bidNtceOrd);
        String resolvedIndustryCode = StringUtils.hasText(industryCode) ? industryCode.trim() : "";

        for (int dayPadding : new int[] {0, 1, 3}) {
            JsonNode matched = scanBidItemInRange(
                    targetNo,
                    targetOrd,
                    announceDate,
                    dayPadding,
                    resolvedIndustryCode
            );
            if (matched != null) {
                return matched;
            }
        }

        throw new BusinessException("공고 상세 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
    }

    private JsonNode scanBidItemInRange(
            String targetNo,
            String targetOrd,
            LocalDate announceDate,
            int dayPadding,
            String industryCode
    ) {
        LocalDate startDate = announceDate.minusDays(dayPadding);
        LocalDate endDate = announceDate.plusDays(dayPadding);
        DateRange range = new DateRange(startDate.atStartOfDay(), endDate.atTime(23, 59));
        int maxPages = Math.min(properties.getMaxPages(), properties.getDetailMaxPages());

        for (int g2bPage = 1; g2bPage <= maxPages; g2bPage++) {
            JsonNode body = callApi(
                    "",
                    targetNo,
                    "",
                    industryCode,
                    "",
                    range,
                    null,
                    null,
                    1,
                    g2bPage,
                    properties.getNumOfRows()
            );
            JsonNode itemsNode = body.path("items");
            if (isEmptyItems(itemsNode)) {
                break;
            }

            for (JsonNode candidate : toItemNodes(itemsNode)) {
                if (targetNo.equals(readText(candidate, "bidNtceNo"))
                        && targetOrd.equals(normalizeBidOrd(readText(candidate, "bidNtceOrd")))) {
                    return candidate;
                }
            }

            int rangeTotal = readInt(body.path("totalCount"));
            if (g2bPage * properties.getNumOfRows() >= rangeTotal) {
                break;
            }
        }

        return null;
    }

    private List<JsonNode> toItemNodes(JsonNode itemsNode) {
        if (itemsNode.isArray()) {
            List<JsonNode> nodes = new ArrayList<>();
            itemsNode.forEach(nodes::add);
            return nodes;
        }
        return List.of(itemsNode);
    }

    private String normalizeBidOrd(String bidNtceOrd) {
        if (!StringUtils.hasText(bidNtceOrd)) {
            return "000";
        }
        String trimmed = bidNtceOrd.trim();
        try {
            return String.format("%03d", Integer.parseInt(trimmed));
        } catch (NumberFormatException e) {
            return trimmed;
        }
    }

    private String resolveDetailContent(
            G2bBidItem item,
            JsonNode rawItem,
            String regionRestriction,
            String industryRestriction
    ) {
        if (StringUtils.hasText(item.getBidNtceDtlCntnts())) {
            return item.getBidNtceDtlCntnts().trim();
        }

        String extracted = extractDetailContent(rawItem);
        if (StringUtils.hasText(extracted)) {
            return extracted;
        }

        return buildDetailContentFallback(item);
    }

    private String buildDetailContentFallback(G2bBidItem item) {
        return "";
    }

    private String resolveRegionRestriction(JsonNode rawItem, String bidNtceNo, String bidNtceOrd) {
        String fromApi = fetchRegionRestrictionDescriptions(bidNtceNo, bidNtceOrd);
        if (StringUtils.hasText(fromApi)) {
            return fromApi;
        }
        return buildRegionRestrictionFromRaw(rawItem);
    }

    private String fetchRegionRestrictionDescriptions(String bidNtceNo, String bidNtceOrd) {
        for (String operation : PRTCPT_PSBL_RGN_OPERATIONS) {
            JsonNode body = tryExecuteBidDetailRequest(operation, bidNtceNo, bidNtceOrd);
            if (body == null) {
                continue;
            }

            JsonNode itemsNode = body.path("items");
            if (isEmptyItems(itemsNode)) {
                continue;
            }

            LinkedHashSet<String> descriptions = new LinkedHashSet<>();
            for (JsonNode item : toItemNodes(itemsNode)) {
                String formatted = formatRegionRestrictionFromNode(item);
                if (StringUtils.hasText(formatted)) {
                    descriptions.add(formatted);
                }
            }
            if (!descriptions.isEmpty()) {
                return String.join("\n\n", descriptions);
            }
        }
        return null;
    }

    private String formatRegionRestrictionFromNode(JsonNode item) {
        String regionName = firstNonBlank(
                readText(item, "prtcptPsblRgnNm"),
                readText(item, "lmtNm"),
                readText(item, "rgnNm")
        );
        String explanation = firstNonBlank(
                readText(item, "prtcptPsblRgnExpln"),
                readText(item, "prtcptPsblRgnExplnCn"),
                readText(item, "prtcptPsblRgnDtls"),
                readText(item, "prtcptPsblRgnCntnts")
        );

        if (!StringUtils.hasText(regionName) && !StringUtils.hasText(explanation)) {
            return null;
        }

        StringBuilder formatted = new StringBuilder();
        if (StringUtils.hasText(regionName)) {
            formatted.append("[").append(regionName.trim()).append("]");
        }
        if (StringUtils.hasText(explanation)) {
            if (formatted.length() > 0) {
                formatted.append("\n");
            }
            formatted.append(formatRegionExplanation(explanation));
        }
        return formatted.toString();
    }

    private String formatRegionExplanation(String explanation) {
        String trimmed = explanation.trim();
        if (trimmed.startsWith("ㆍ") || trimmed.startsWith("·") || trimmed.startsWith("-")) {
            return trimmed;
        }
        return "ㆍ" + trimmed;
    }

    private JsonNode selectIndustryRestrictionSource(JsonNode servcItem, JsonNode rawItem) {
        if (servcItem != null && hasIndustryRestrictionFields(servcItem)) {
            return servcItem;
        }
        return rawItem;
    }

    private boolean hasIndustryRestrictionFields(JsonNode item) {
        if (item == null) {
            return false;
        }

        if (StringUtils.hasText(firstNonBlank(
                readText(item, "indstrytyLmtCntnts"),
                readText(item, "bidprcPsblIndstrytyNm")
        ))) {
            return true;
        }

        for (int index = 1; index <= 10; index++) {
            if (StringUtils.hasText(firstNonBlank(
                    readText(item, "indstrytyNm" + index),
                    readText(item, "bidprcPsblIndstrytyNm" + index),
                    readText(item, "lcnsLmtNm" + index),
                    readText(item, "indstrytyLmtNm" + index)
            ))) {
                return true;
            }
        }

        return StringUtils.hasText(firstNonBlank(
                readText(item, "indstrytyNm"),
                readText(item, "lcnsLmtNm"),
                readText(item, "indstrytyLmtNm")
        ));
    }

    private JsonNode fetchServcBidItemNode(String bidNtceNo, String bidNtceOrd) {
        try {
            JsonNode body = executeRequest(buildServcBidLookupUri(bidNtceNo, bidNtceOrd));
            JsonNode itemsNode = body.path("items");
            if (isEmptyItems(itemsNode)) {
                return null;
            }

            String targetOrd = normalizeBidOrd(bidNtceOrd);
            for (JsonNode item : toItemNodes(itemsNode)) {
                if (bidNtceNo.equals(readText(item, "bidNtceNo"))
                        && targetOrd.equals(normalizeBidOrd(readText(item, "bidNtceOrd")))) {
                    return item;
                }
            }
            return toItemNodes(itemsNode).get(0);
        } catch (BusinessException e) {
            log.debug("{} 조회 실패 bidNtceNo={}: {}", SERVC_DETAIL_OPERATION, bidNtceNo, e.getMessage());
            return null;
        }
    }

    private URI buildServcBidLookupUri(String bidNtceNo, String bidNtceOrd) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        StringBuilder url = new StringBuilder(baseUrl)
                .append("/")
                .append(SERVC_DETAIL_OPERATION)
                .append("?serviceKey=")
                .append(encodeQueryValue(properties.getServiceKey()))
                .append("&pageNo=1")
                .append("&numOfRows=10")
                .append("&inqryDiv=3")
                .append("&bidNtceNo=").append(encodeQueryValue(bidNtceNo))
                .append("&type=json");

        if (StringUtils.hasText(bidNtceOrd)) {
            url.append("&bidNtceOrd=").append(encodeQueryValue(normalizeBidOrd(bidNtceOrd)));
        }

        return URI.create(url.toString());
    }

    private String resolveIndustryRestriction(
            JsonNode rawItem,
            String bidNtceNo,
            String bidNtceOrd
    ) {
        String fromApi = fetchIndustryRestrictionDescriptions(bidNtceNo, bidNtceOrd);
        if (StringUtils.hasText(fromApi)) {
            return fromApi;
        }

        String fromRaw = buildIndustryRestrictionFromRaw(rawItem);
        if (StringUtils.hasText(fromRaw)) {
            return fromRaw;
        }

        if (isYes(readText(rawItem, "indstrytyLmtYn"))) {
            return "업종 제한 있음";
        }

        return "제한없음";
    }

    private String fetchIndustryRestrictionDescriptions(String bidNtceNo, String bidNtceOrd) {
        for (String operation : BID_PRC_PSBL_INDSTRYTY_OPERATIONS) {
            JsonNode body = tryExecuteBidDetailRequest(operation, bidNtceNo, bidNtceOrd);
            if (body == null) {
                continue;
            }

            JsonNode itemsNode = body.path("items");
            if (isEmptyItems(itemsNode)) {
                continue;
            }

            String preformatted = extractPreformattedIndustryRestriction(itemsNode);
            if (StringUtils.hasText(preformatted)) {
                return preformatted;
            }

            LinkedHashSet<String> labels = new LinkedHashSet<>();
            for (JsonNode item : toItemNodes(itemsNode)) {
                collectIndustryLabels(item, labels);
            }

            String combined = formatCombinedIndustryRestriction(labels);
            if (StringUtils.hasText(combined)) {
                return combined;
            }
        }
        return null;
    }

    private String extractPreformattedIndustryRestriction(JsonNode itemsNode) {
        for (JsonNode item : toItemNodes(itemsNode)) {
            String preformatted = firstNonBlank(
                    readText(item, "indstrytyLmtCntnts"),
                    readText(item, "bidprcPsblIndstrytyNm")
            );
            if (StringUtils.hasText(preformatted) && preformatted.contains("업종을 등록한 업체")) {
                return preformatted.trim();
            }
        }
        return null;
    }

    private void collectIndustryLabels(JsonNode item, LinkedHashSet<String> labels) {
        addIndustryLabel(labels,
                firstNonBlank(
                        readText(item, "indstrytyNm"),
                        readText(item, "lcnsLmtNm"),
                        readText(item, "indstrytyLmtNm"),
                        readText(item, "bidprcPsblIndstrytyNm")
                ),
                firstNonBlank(
                        readText(item, "indstrytyCd"),
                        readText(item, "lcnsLmtCd"),
                        readText(item, "indstrytyLmtCd"),
                        readText(item, "bidprcPsblIndstrytyCd")
                )
        );

        for (int index = 1; index <= 10; index++) {
            addIndustryLabel(labels,
                    firstNonBlank(
                            readText(item, "indstrytyNm" + index),
                            readText(item, "bidprcPsblIndstrytyNm" + index),
                            readText(item, "lcnsLmtNm" + index),
                            readText(item, "indstrytyLmtNm" + index)
                    ),
                    firstNonBlank(
                            readText(item, "indstrytyCd" + index),
                            readText(item, "bidprcPsblIndstrytyCd" + index),
                            readText(item, "lcnsLmtCd" + index),
                            readText(item, "indstrytyLmtCd" + index)
                    )
            );
        }
    }

    private void addIndustryLabel(LinkedHashSet<String> labels, String name, String code) {
        String label = formatIndustryLabel(name, code);
        if (StringUtils.hasText(label)) {
            labels.add(label);
        }
    }

    private String formatIndustryLabel(String name, String code) {
        if (!StringUtils.hasText(name) && !StringUtils.hasText(code)) {
            return null;
        }

        StringBuilder label = new StringBuilder();
        if (StringUtils.hasText(name)) {
            label.append(name.trim());
        }
        if (StringUtils.hasText(code)) {
            label.append("(").append(code.trim()).append(")");
        }
        return label.toString();
    }

    private String formatCombinedIndustryRestriction(LinkedHashSet<String> labels) {
        if (labels.isEmpty()) {
            return null;
        }
        if (labels.size() == 1) {
            return "[ " + labels.iterator().next() + " ] 업종을 등록한 업체";
        }
        return "[ " + String.join("과 ", labels) + " ] 업종을 등록한 업체";
    }

    private String buildIndustryRestrictionFromRaw(JsonNode rawItem) {
        String preformatted = firstNonBlank(
                readText(rawItem, "indstrytyLmtCntnts"),
                readText(rawItem, "bidprcPsblIndstrytyNm")
        );
        if (StringUtils.hasText(preformatted) && preformatted.contains("업종을 등록한 업체")) {
            return preformatted.trim();
        }

        LinkedHashSet<String> labels = new LinkedHashSet<>();
        collectIndustryLabels(rawItem, labels);
        return formatCombinedIndustryRestriction(labels);
    }

    private String buildRegionRestrictionFromRaw(JsonNode rawItem) {
        LinkedHashSet<String> descriptions = new LinkedHashSet<>();

        String single = formatRegionRestrictionFromNode(rawItem);
        if (StringUtils.hasText(single)) {
            descriptions.add(single);
        }

        for (int index = 1; index <= 5; index++) {
            String regionName = firstNonBlank(
                    readText(rawItem, "prtcptPsblRgnNm" + index),
                    readText(rawItem, "jntcontrctDutyRgnNm" + index)
            );
            String explanation = firstNonBlank(
                    readText(rawItem, "prtcptPsblRgnExpln" + index),
                    readText(rawItem, "prtcptPsblRgnCntnts" + index)
            );
            if (!StringUtils.hasText(regionName) && !StringUtils.hasText(explanation)) {
                continue;
            }

            StringBuilder formatted = new StringBuilder();
            if (StringUtils.hasText(regionName)) {
                formatted.append("[").append(regionName.trim()).append("]");
            }
            if (StringUtils.hasText(explanation)) {
                if (formatted.length() > 0) {
                    formatted.append("\n");
                }
                formatted.append(formatRegionExplanation(explanation));
            } else if (StringUtils.hasText(readText(rawItem, "rgnLmtBidLocplcJdgmBssNm"))) {
                if (formatted.length() > 0) {
                    formatted.append("\n");
                }
                formatted.append(formatRegionExplanation(readText(rawItem, "rgnLmtBidLocplcJdgmBssNm")));
            }
            descriptions.add(formatted.toString());
        }

        if (!descriptions.isEmpty()) {
            return String.join("\n\n", descriptions);
        }

        if (isYes(readText(rawItem, "cmmnSpldmdCorpRgnLmtYn"))
                || isYes(readText(rawItem, "bidPrtcptLmtYn"))) {
            String explanation = readText(rawItem, "rgnLmtBidLocplcJdgmBssNm");
            if (StringUtils.hasText(explanation)) {
                return formatRegionExplanation(explanation);
            }
        }
        return "제한없음";
    }

    private JsonNode tryExecuteBidDetailRequest(String operation, String bidNtceNo, String bidNtceOrd) {
        try {
            return executeRequest(buildBidDetailSubUri(operation, bidNtceNo, bidNtceOrd));
        } catch (BusinessException e) {
            log.debug("{} 조회 실패 bidNtceNo={}: {}", operation, bidNtceNo, e.getMessage());
            return null;
        }
    }

    private URI buildBidDetailSubUri(String operation, String bidNtceNo, String bidNtceOrd) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        StringBuilder url = new StringBuilder(baseUrl)
                .append("/")
                .append(operation)
                .append("?serviceKey=")
                .append(encodeQueryValue(properties.getServiceKey()))
                .append("&pageNo=1")
                .append("&numOfRows=100")
                .append("&inqryDiv=2")
                .append("&bidNtceNo=").append(encodeQueryValue(bidNtceNo))
                .append("&type=json");

        if (StringUtils.hasText(bidNtceOrd)) {
            url.append("&bidNtceOrd=").append(encodeQueryValue(normalizeBidOrd(bidNtceOrd)));
        }

        return URI.create(url.toString());
    }

    private URI buildBidDetailSubUri(String operation, String bidNtceNo) {
        return buildBidDetailSubUri(operation, bidNtceNo, null);
    }

    private void collectTextValues(JsonNode rawItem, Set<String> values, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = readText(rawItem, fieldName);
            if (StringUtils.hasText(value)) {
                values.add(value.trim());
            }
        }
    }

    private boolean isYes(String value) {
        return "Y".equalsIgnoreCase(StringUtils.hasText(value) ? value.trim() : "");
    }

    private void appendDetailFact(StringBuilder html, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        html.append("<li><strong>")
                .append(escapeHtml(label))
                .append(":</strong> ")
                .append(escapeHtml(value.trim()))
                .append("</li>");
    }

    private void appendDetailFactAlways(StringBuilder html, String label, String value) {
        html.append("<li><strong>")
                .append(escapeHtml(label))
                .append(":</strong> ")
                .append(escapeHtml(StringUtils.hasText(value) ? value.trim() : "-"))
                .append("</li>");
    }

    private void appendDetailFactAlwaysMultiline(StringBuilder html, String label, String value) {
        html.append("<li><strong>")
                .append(escapeHtml(label))
                .append(":</strong> ");
        if (!StringUtils.hasText(value)) {
            html.append("-");
        } else {
            String[] lines = value.split("\n");
            for (int index = 0; index < lines.length; index++) {
                if (index > 0) {
                    html.append("<br/>");
                }
                html.append(escapeHtml(lines[index].trim()));
            }
        }
        html.append("</li>");
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String extractDetailContent(JsonNode item) {
        for (String fieldName : List.of("bidNtceDtlCntnts", "bidNtceExpln", "bidNtceStylCntnts")) {
            String value = readText(item, fieldName);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private JsonNode callApi(
            String bidName,
            String bidNo,
            String industry,
            String industryCode,
            String contractMethod,
            DateRange range,
            Long minPrice,
            Long maxPrice,
            int inqryDiv,
            int pageNo,
            int numOfRows
    ) {
        return executeRequest(buildRequestUri(
                bidName,
                bidNo,
                industry,
                industryCode,
                contractMethod,
                range,
                minPrice,
                maxPrice,
                inqryDiv,
                pageNo,
                numOfRows
        ));
    }

    private JsonNode executeRequest(URI requestUri) {
        String raw;
        try {
            raw = g2bRestClient.get()
                    .uri(requestUri)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            log.error("G2B API call failed: {}", requestUri, e);
            throw new BusinessException("나라장터 API 호출에 실패했습니다.", HttpStatus.BAD_GATEWAY);
        }

        if (!StringUtils.hasText(raw)) {
            throw new BusinessException("나라장터 API 응답이 없습니다.", HttpStatus.BAD_GATEWAY);
        }

        JsonNode root = jsonMapper.readTree(raw);
        JsonNode responseNode = root.path("response");
        if (responseNode.isMissingNode()) {
            JsonNode errorHeader = root.path("nkoneps.com.response.ResponseError").path("header");
            if (!errorHeader.isMissingNode()) {
                throw new BusinessException("나라장터 API 오류: " + errorHeader.path("resultMsg").asText(), HttpStatus.BAD_GATEWAY);
            }
            throw new BusinessException("나라장터 API 응답 형식이 올바르지 않습니다.", HttpStatus.BAD_GATEWAY);
        }

        JsonNode header = responseNode.path("header");
        String resultCode = header.path("resultCode").asText();
        if (!"00".equals(resultCode)) {
            throw new BusinessException("나라장터 API 오류: " + header.path("resultMsg").asText(), HttpStatus.BAD_GATEWAY);
        }

        return responseNode.path("body");
    }

    private List<G2bAttachment> parseAttachments(JsonNode item) {
        List<G2bAttachment> attachments = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        for (int index = 1; index <= 10; index++) {
            String fileName = readText(item, "ntceSpecFileNm" + index);
            String docUrl = readText(item, "ntceSpecDocUrl" + index);
            String fileUrl = readText(item, "ntceSpecFileUrl" + index);
            addAttachment(attachments, seenUrls, fileName, firstNonBlank(fileUrl, docUrl));
            addAttachment(
                    attachments,
                    seenUrls,
                    readText(item, "ntceSpecDocNm" + index),
                    docUrl
            );
            addAttachment(
                    attachments,
                    seenUrls,
                    readText(item, "bidNtceSpecDocNm" + index),
                    readText(item, "bidNtceSpecDocUrl" + index)
            );
        }

        addAttachment(attachments, seenUrls, readText(item, "stdNtceDocNm"), readText(item, "stdNtceDocUrl"));

        return attachments;
    }

    private void appendEorderAttachments(List<G2bAttachment> attachments, String bidNtceNo) {
        if (!StringUtils.hasText(bidNtceNo)) {
            return;
        }

        Set<String> seenUrls = new HashSet<>();
        for (G2bAttachment attachment : attachments) {
            seenUrls.add(attachment.getFileUrl());
        }

        JsonNode body;
        try {
            body = executeRequest(buildEorderAtchUri(bidNtceNo.trim()));
        } catch (BusinessException e) {
            log.debug("e발주 첨부파일 조회 실패 bidNtceNo={}: {}", bidNtceNo, e.getMessage());
            return;
        }

        JsonNode itemsNode = body.path("items");
        if (isEmptyItems(itemsNode)) {
            return;
        }

        for (JsonNode eorderItem : toItemNodes(itemsNode)) {
            String fileName = readText(eorderItem, "eorderAtchFileNm");
            String fileUrl = readText(eorderItem, "eorderAtchFileUrl");
            if (!StringUtils.hasText(fileName) || !StringUtils.hasText(fileUrl)) {
                continue;
            }

            String docDiv = readText(eorderItem, "eorderDocDivNm");
            String displayName = StringUtils.hasText(docDiv)
                    ? "[" + docDiv.trim() + "] " + fileName.trim()
                    : "[제안요청정보] " + fileName.trim();
            addAttachment(attachments, seenUrls, displayName, fileUrl);
        }
    }

    private URI buildEorderAtchUri(String bidNtceNo) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        StringBuilder url = new StringBuilder(baseUrl)
                .append("/")
                .append(EORDER_ATCH_OPERATION)
                .append("?serviceKey=")
                .append(encodeQueryValue(properties.getServiceKey()))
                .append("&pageNo=1")
                .append("&numOfRows=10")
                .append("&inqryDiv=2")
                .append("&bidNtceNo=").append(encodeQueryValue(bidNtceNo))
                .append("&type=json");

        return URI.create(url.toString());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private void addAttachment(
            List<G2bAttachment> attachments,
            Set<String> seenUrls,
            String fileName,
            String fileUrl
    ) {
        if (!StringUtils.hasText(fileUrl) || seenUrls.contains(fileUrl)) {
            return;
        }

        seenUrls.add(fileUrl);
        String resolvedName = StringUtils.hasText(fileName) ? fileName.trim() : extractFileName(fileUrl);
        if ("downloadFile.do".equalsIgnoreCase(resolvedName)) {
            resolvedName = "첨부파일 " + (attachments.size() + 1);
        }

        attachments.add(G2bAttachment.builder()
                .fileName(resolvedName)
                .fileUrl(fileUrl.trim())
                .build());
    }

    private String readText(JsonNode node, String fieldName) {
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }

        String value = valueNode.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private String extractFileName(String fileUrl) {
        int queryIndex = fileUrl.indexOf('?');
        String path = queryIndex >= 0 ? fileUrl.substring(0, queryIndex) : fileUrl;
        int slashIndex = path.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < path.length() - 1) {
            return path.substring(slashIndex + 1);
        }
        return "첨부파일";
    }

    private URI buildRequestUri(
            String bidName,
            String bidNo,
            String industry,
            String industryCode,
            String contractMethod,
            DateRange range,
            Long minPrice,
            Long maxPrice,
            int inqryDiv,
            int pageNo,
            int numOfRows
    ) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        StringBuilder url = new StringBuilder(baseUrl)
                .append("/")
                .append(properties.getOperation())
                .append("?serviceKey=")
                .append(encodeQueryValue(properties.getServiceKey()))
                .append("&pageNo=").append(pageNo)
                .append("&numOfRows=").append(numOfRows)
                .append("&inqryDiv=").append(inqryDiv)
                .append("&inqryBgnDt=").append(range.start().format(API_DATE_TIME))
                .append("&inqryEndDt=").append(range.end().format(API_DATE_TIME))
                .append("&type=json");

        if (StringUtils.hasText(bidName)) {
            url.append("&bidNtceNm=").append(encodeQueryValue(bidName.trim()));
        }
        if (StringUtils.hasText(bidNo)) {
            url.append("&bidNtceNo=").append(encodeQueryValue(bidNo.trim()));
        }
        if (StringUtils.hasText(industryCode)) {
            url.append("&indstrytyCd=").append(encodeQueryValue(industryCode.trim()));
        } else if (StringUtils.hasText(industry)) {
            url.append("&pubPrcrmntClsfcNm=").append(encodeQueryValue(industry.trim()));
        }
        if (minPrice != null) {
            url.append("&presmptPrceBgn=").append(minPrice);
        }
        if (maxPrice != null) {
            url.append("&presmptPrceEnd=").append(maxPrice);
        }

        return URI.create(url.toString());
    }

    private String encodeQueryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean isEmptyItems(JsonNode itemsNode) {
        if (itemsNode.isTextual()) {
            return !StringUtils.hasText(itemsNode.asText());
        }
        if (itemsNode.isArray()) {
            return itemsNode.isEmpty();
        }
        return false;
    }

    private List<G2bBidItem> parseItems(JsonNode itemsNode) {
        List<G2bBidItem> items = new ArrayList<>();
        if (itemsNode.isMissingNode() || itemsNode.isNull()) {
            return items;
        }

        if (itemsNode.isArray()) {
            for (JsonNode child : itemsNode) {
                items.add(jsonMapper.treeToValue(child, G2bBidItem.class));
            }
            return items;
        }

        if (itemsNode.isObject()) {
            items.add(jsonMapper.treeToValue(itemsNode, G2bBidItem.class));
        }

        return items;
    }

    private int readInt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return 0;
        }
        return node.asInt();
    }

    private List<G2bBidItem> distinctItems(List<G2bBidItem> items) {
        Set<String> seen = new HashSet<>();
        List<G2bBidItem> distinct = new ArrayList<>();
        for (G2bBidItem item : items) {
            if (seen.add(buildItemKey(item))) {
                distinct.add(item);
            }
        }
        return distinct;
    }

    private String buildItemKey(G2bBidItem item) {
        String bidNo = StringUtils.hasText(item.getBidNtceNo()) ? item.getBidNtceNo().trim() : "";
        String bidOrd = StringUtils.hasText(item.getBidNtceOrd()) ? item.getBidNtceOrd().trim() : "0";
        String announceDate = StringUtils.hasText(item.getBidNtceDt()) ? item.getBidNtceDt().trim() : "";
        return bidNo + "|" + bidOrd + "|" + announceDate;
    }

    private boolean matchesSearchFilters(
            G2bBidItem item,
            String industry,
            String industryCode,
            String contractMethod,
            boolean excludeClosedBids
    ) {
        return matchesIndustry(item, industry, industryCode)
                && matchesContractMethod(item, contractMethod)
                && matchesServiceDivision(item)
                && matchesOpenBid(item, excludeClosedBids);
    }

    private boolean matchesOpenBid(G2bBidItem item, boolean excludeClosedBids) {
        if (!excludeClosedBids || !StringUtils.hasText(item.getBidClseDt())) {
            return true;
        }
        try {
            LocalDateTime closeAt = LocalDateTime.parse(item.getBidClseDt().trim(), BID_CLOSE_DATE_TIME);
            return !closeAt.isBefore(LocalDateTime.now());
        } catch (DateTimeParseException e) {
            return true;
        }
    }

    private boolean matchesServiceDivision(G2bBidItem item) {
        if (!StringUtils.hasText(item.getSrvceDivNm())) {
            return false;
        }
        return ALLOWED_SERVICE_DIVISIONS.contains(item.getSrvceDivNm().trim());
    }

    private boolean matchesContractMethod(G2bBidItem item, String contractMethod) {
        if (!requiresClientSideContractFilter(contractMethod)) {
            return true;
        }
        String actual = StringUtils.hasText(item.getCntrctCnclsMthdNm())
                ? item.getCntrctCnclsMthdNm().trim()
                : "";
        return contractMethod.trim().equals(actual);
    }

    private boolean matchesIndustry(G2bBidItem item, String industry, String industryCode) {
        if (StringUtils.hasText(industryCode)) {
            return true;
        }
        if (!StringUtils.hasText(industry)) {
            return true;
        }
        String keyword = industry.trim();
        return contains(item.getPubPrcrmntLrgClsfcNm(), keyword)
                || contains(item.getPubPrcrmntMidClsfcNm(), keyword)
                || contains(item.getPubPrcrmntClsfcNm(), keyword);
    }

    private boolean contains(String source, String keyword) {
        return StringUtils.hasText(source) && source.contains(keyword);
    }

    private List<DateRange> splitDateRange(LocalDate startDate, LocalDate endDate, int maxDays) {
        List<DateRange> ranges = new ArrayList<>();
        LocalDate currentStart = startDate;

        while (!currentStart.isAfter(endDate)) {
            LocalDate currentEnd = currentStart.plusDays(maxDays - 1L);
            if (currentEnd.isAfter(endDate)) {
                currentEnd = endDate;
            }
            ranges.add(new DateRange(
                    currentStart.atTime(0, 0),
                    currentEnd.atTime(23, 59)
            ));
            currentStart = currentEnd.plusDays(1);
        }

        return ranges;
    }

    private record DateRange(java.time.LocalDateTime start, java.time.LocalDateTime end) {
    }
}
