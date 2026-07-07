package com.nara.nara_be.service;

import com.nara.nara_be.dto.BidDateQueryType;
import com.nara.nara_be.dto.BidSearchRequest;
import com.nara.nara_be.dto.DlSrchParamM;
import com.nara.nara_be.dto.ResolvedBidSearch;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BidSearchParamResolverTest {

    private final BidSearchParamResolver resolver = new BidSearchParamResolver();

    @Test
    void resolve_mapsDlSrchParamMToInternalSearch() {
        DlSrchParamM param = new DlSrchParamM();
        param.setCurrentPage(1);
        param.setRecordCountPerPage("100");
        param.setStartBizYmd("20260607");
        param.setEndBizYmd("20260708");
        param.setMindCd("1468");
        param.setMindNm("소프트웨어사업자(컴퓨터관련서비스사업)");
        param.setBizYmdDiv("pbancDt");
        param.setPrcmBsneAreaCd("조070002 조070003");
        param.setBidCtrtMthdCd("");
        param.setUntySrchSeCd("BK BUK");
        param.setFrcpYn("N");
        param.setLaseYn("N");
        param.setRsrvYn("N");

        ResolvedBidSearch resolved = resolver.resolve(param);

        assertThat(resolved.getStartDate()).isEqualTo(LocalDate.of(2026, 6, 7));
        assertThat(resolved.getEndDate()).isEqualTo(LocalDate.of(2026, 7, 8));
        assertThat(resolved.getIndustryCode()).isEqualTo("1468");
        assertThat(resolved.getIndustry()).isEqualTo("소프트웨어사업자(컴퓨터관련서비스사업)");
        assertThat(resolved.getContractMethod()).isEqualTo("전체");
        assertThat(resolved.getPageNo()).isEqualTo(1);
        assertThat(resolved.getPageSize()).isEqualTo(100);
        assertThat(resolved.isExcludeClosedBids()).isTrue();
        assertThat(resolved.getDateQueryType()).isEqualTo(BidDateQueryType.ANNOUNCEMENT);
    }

    @Test
    void resolve_excludesClosedBidsByDefaultWhenFlagMissing() {
        DlSrchParamM param = sampleParam();

        ResolvedBidSearch resolved = resolver.resolve(param);

        assertThat(resolved.isExcludeClosedBids()).isTrue();
    }

    @Test
    void resolve_includesClosedBidsWhenExplicitlyDisabled() {
        DlSrchParamM param = sampleParam();
        param.setSlpRDdlnExclYn("N");

        ResolvedBidSearch resolved = resolver.resolve(param);

        assertThat(resolved.isExcludeClosedBids()).isFalse();
    }

    @Test
    void resolve_mapsContractMethodCode() {
        DlSrchParamM param = sampleParam();
        param.setBidCtrtMthdCd("계030004");

        ResolvedBidSearch resolved = resolver.resolve(param);

        assertThat(resolved.getContractMethod()).isEqualTo("수의계약");
    }

    @Test
    void resolve_mapsExcludeClosedBids() {
        DlSrchParamM param = sampleParam();
        param.setSlpRDdlnExclYn("Y");

        ResolvedBidSearch resolved = resolver.resolve(param);

        assertThat(resolved.isExcludeClosedBids()).isTrue();
    }

    @Test
    void resolve_mapsOpeningDateQueryType() {
        DlSrchParamM param = sampleParam();
        param.setBizYmdDiv("opengDt");

        ResolvedBidSearch resolved = resolver.resolve(param);

        assertThat(resolved.getDateQueryType()).isEqualTo(BidDateQueryType.OPENING);
    }

    @Test
    void resolve_mapsEstimatedPriceRangeFromBidSearchRequest() throws Exception {
        tools.jackson.databind.json.JsonMapper jsonMapper = tools.jackson.databind.json.JsonMapper.builder().build();
        String json = """
                {
                  "startDate": "2026-06-07",
                  "endDate": "2026-07-08",
                  "industryCode": "1468",
                  "dateType": "announceDate",
                  "minPrice": 500000000,
                  "maxPrice": 1000000000
                }
                """;

        BidSearchRequest request = jsonMapper.readValue(json, BidSearchRequest.class);
        ResolvedBidSearch resolved = resolver.resolve(request);

        assertThat(resolved.getMinPrice()).isEqualTo(500_000_000L);
        assertThat(resolved.getMaxPrice()).isEqualTo(1_000_000_000L);
    }

    @Test
    void resolve_defaultsToAnnouncementWhenBizYmdDivMissing() {
        DlSrchParamM param = sampleParam();
        param.setBizYmdDiv(null);

        ResolvedBidSearch resolved = resolver.resolve(param);

        assertThat(resolved.getDateQueryType()).isEqualTo(BidDateQueryType.ANNOUNCEMENT);
    }

    private DlSrchParamM sampleParam() {
        DlSrchParamM param = new DlSrchParamM();
        param.setStartBizYmd("20260607");
        param.setEndBizYmd("20260708");
        param.setMindCd("1468");
        param.setBizYmdDiv("pbancDt");
        return param;
    }
}
