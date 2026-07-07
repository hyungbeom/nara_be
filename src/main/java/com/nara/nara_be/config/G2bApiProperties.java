package com.nara.nara_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "g2b.api")
public class G2bApiProperties {

    private String baseUrl = "https://apis.data.go.kr/1230000/ad/BidPublicInfoService";
    private String serviceKey;
    /** 용역(일반용역·기술용역) 입찰공고 검색 — srvceDivNm 으로 구분 */
    private String operation = "getBidPblancListInfoServcPPSSrch";
    private int numOfRows = 100;
    private int maxPages = 10;
    private int maxDaysPerRequest = 30;
    private int maxPageSize = 100;
    private int maxParallelRequests = 4;
    private int searchCacheMinutes = 5;
    private int detailMaxPages = 5;
}
