package com.nara.nara_be.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;

/**
 * 나라장터 dlSrchParamM 검색 요청.
 * <ul>
 *   <li>{@code { "dlSrchParamM": { ... } }} — UI 래핑 형식</li>
 *   <li>{@code { "startBizYmd": "20260607", ... }} — dlSrchParamM 필드 평면 형식</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BidSearchRequest extends DlSrchParamM {

    /** 이전 API 호환 */
    @JsonAlias({"startDate"})
    private String legacyStartDate;

    @JsonAlias({"endDate"})
    private String legacyEndDate;

    @JsonAlias({"industryCode"})
    private String legacyIndustryCode;

    @JsonAlias({"industry"})
    private String legacyIndustry;

    @JsonAlias({"bidName"})
    private String legacyBidName;

    @JsonAlias({"pageNo"})
    private Integer legacyPageNo;

    @JsonAlias({"pageSize"})
    private Integer legacyPageSize;

    /** 이전 API: announceDate(공고일자) | openingDate(개찰일자) */
    @JsonAlias({"dateType"})
    private String legacyDateType;

    private Long minPrice;
    private Long maxPrice;

    @JsonProperty("dlSrchParamM")
    public void setDlSrchParamM(DlSrchParamM nested) {
        if (nested != null) {
            BeanUtils.copyProperties(nested, this);
        }
    }

    public void applyLegacyFields() {
        if (!StringUtils.hasText(getStartBizYmd()) && StringUtils.hasText(legacyStartDate)) {
            setStartBizYmd(toBizYmd(legacyStartDate));
        }
        if (!StringUtils.hasText(getEndBizYmd()) && StringUtils.hasText(legacyEndDate)) {
            setEndBizYmd(toBizYmd(legacyEndDate));
        }
        if (!StringUtils.hasText(getMindCd()) && StringUtils.hasText(legacyIndustryCode)) {
            setMindCd(legacyIndustryCode);
        }
        if (!StringUtils.hasText(getMindNm()) && StringUtils.hasText(legacyIndustry)) {
            setMindNm(legacyIndustry);
        }
        if (!StringUtils.hasText(getBizNm()) && StringUtils.hasText(legacyBidName)) {
            setBizNm(legacyBidName);
        }
        if ((getCurrentPage() == null || getCurrentPage() <= 0) && legacyPageNo != null && legacyPageNo > 0) {
            setCurrentPage(legacyPageNo);
        }
        if (!StringUtils.hasText(getRecordCountPerPage()) && legacyPageSize != null && legacyPageSize > 0) {
            setRecordCountPerPage(String.valueOf(legacyPageSize));
        }
        if (!StringUtils.hasText(getBizYmdDiv()) && StringUtils.hasText(legacyDateType)) {
            setBizYmdDiv(mapLegacyDateType(legacyDateType.trim()));
        }
    }

    private String mapLegacyDateType(String dateType) {
        return switch (dateType) {
            case "openingDate", "opengDt" -> "opengDt";
            default -> "pbancDt";
        };
    }

    public boolean hasDlSrchParams() {
        return StringUtils.hasText(getStartBizYmd()) && StringUtils.hasText(getEndBizYmd());
    }

    private String toBizYmd(String isoDate) {
        return isoDate.replace("-", "");
    }
}
