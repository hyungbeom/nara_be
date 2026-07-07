package com.nara.nara_be.service;

import com.nara.nara_be.dto.BidDateQueryType;
import com.nara.nara_be.dto.BidSearchRequest;
import com.nara.nara_be.dto.DlSrchParamM;
import com.nara.nara_be.dto.ResolvedBidSearch;
import com.nara.nara_be.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Component
public class BidSearchParamResolver {

    private static final DateTimeFormatter BASIC_YMD = DateTimeFormatter.BASIC_ISO_DATE;

    /** 나라장터 UI 계약방법 코드 → Open API 응답 cntrctCnclsMthdNm */
    private static final Map<String, String> CONTRACT_METHOD_BY_CODE = Map.of(
            "계030001", "일반경쟁",
            "계030002", "제한경쟁",
            "계030003", "지명경쟁",
            "계030004", "수의계약"
    );

    public ResolvedBidSearch resolve(BidSearchRequest request) {
        if (request != null) {
            request.applyLegacyFields();
        }
        return resolveDlSrchParam(request);
    }

    public ResolvedBidSearch resolve(DlSrchParamM param) {
        return resolveDlSrchParam(param);
    }

    private ResolvedBidSearch resolveDlSrchParam(DlSrchParamM param) {
        if (param == null || !StringUtils.hasText(param.getStartBizYmd()) || !StringUtils.hasText(param.getEndBizYmd())) {
            throw new BusinessException("dlSrchParamM은 필수입니다.", HttpStatus.BAD_REQUEST);
        }

        BidDateQueryType dateQueryType = resolveDateQueryType(param.getBizYmdDiv());

        LocalDate startDate = parseBizYmd(param.getStartBizYmd(), "startBizYmd");
        LocalDate endDate = parseBizYmd(param.getEndBizYmd(), "endBizYmd");
        if (startDate.isAfter(endDate)) {
            throw new BusinessException("startBizYmd는 endBizYmd보다 이후일 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        return ResolvedBidSearch.builder()
                .bidName(trimToNull(param.getBizNm()))
                .bidNo(trimToNull(param.getBizNo()))
                .industry(StringUtils.hasText(param.getMindNm())
                        ? param.getMindNm().trim()
                        : ResolvedBidSearch.DEFAULT_INDUSTRY_NAME)
                .industryCode(StringUtils.hasText(param.getMindCd())
                        ? param.getMindCd().trim()
                        : ResolvedBidSearch.DEFAULT_INDUSTRY_CODE)
                .contractMethod(resolveContractMethod(param.getBidCtrtMthdCd()))
                .startDate(startDate)
                .endDate(endDate)
                .dateQueryType(dateQueryType)
                .minPrice(null)
                .maxPrice(null)
                .pageNo(param.getCurrentPage() != null && param.getCurrentPage() > 0 ? param.getCurrentPage() : 1)
                .pageSize(parsePageSize(param.getRecordCountPerPage()))
                .excludeClosedBids(resolveExcludeClosedBids(param.getSlpRDdlnExclYn()))
                .build();
    }

    /** 나라장터 UI 기본값: 입찰마감제외 체크(slpRDdlnExclYn 미전송 시 Y) */
    private boolean resolveExcludeClosedBids(String slpRDdlnExclYn) {
        if (!StringUtils.hasText(slpRDdlnExclYn)) {
            return true;
        }
        return isYes(slpRDdlnExclYn);
    }

    private BidDateQueryType resolveDateQueryType(String bizYmdDiv) {
        if (!StringUtils.hasText(bizYmdDiv) || "pbancDt".equals(bizYmdDiv.trim())) {
            return BidDateQueryType.ANNOUNCEMENT;
        }
        if ("opengDt".equals(bizYmdDiv.trim())) {
            return BidDateQueryType.OPENING;
        }
        throw new BusinessException(
                "bizYmdDiv는 pbancDt(공고일자) 또는 opengDt(개찰일자)만 지원합니다.",
                HttpStatus.BAD_REQUEST
        );
    }

    private String resolveContractMethod(String bidCtrtMthdCd) {
        if (!StringUtils.hasText(bidCtrtMthdCd)) {
            return ResolvedBidSearch.CONTRACT_METHOD_ALL;
        }
        String code = bidCtrtMthdCd.trim();
        String name = CONTRACT_METHOD_BY_CODE.get(code);
        if (name == null) {
            throw new BusinessException("지원하지 않는 계약방법 코드입니다: " + code, HttpStatus.BAD_REQUEST);
        }
        return name;
    }

    private int parsePageSize(String recordCountPerPage) {
        if (!StringUtils.hasText(recordCountPerPage)) {
            return 100;
        }
        try {
            int pageSize = Integer.parseInt(recordCountPerPage.trim());
            return pageSize > 0 ? pageSize : 100;
        } catch (NumberFormatException e) {
            throw new BusinessException("recordCountPerPage 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private LocalDate parseBizYmd(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(fieldName + "은(는) 필수입니다.", HttpStatus.BAD_REQUEST);
        }
        String trimmed = value.trim();
        try {
            if (trimmed.length() == 8) {
                return LocalDate.parse(trimmed, BASIC_YMD);
            }
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException e) {
            throw new BusinessException(fieldName + " 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private boolean isYes(String value) {
        return "Y".equalsIgnoreCase(StringUtils.hasText(value) ? value.trim() : "");
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
