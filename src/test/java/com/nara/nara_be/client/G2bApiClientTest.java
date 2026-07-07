package com.nara.nara_be.client;

import com.nara.nara_be.config.G2bApiProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class G2bApiClientTest {

    private static String emptyItemsJson() {
        return """
                {
                  "response": {
                    "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
                    "body": { "totalCount": 0 }
                  }
                }
                """;
    }

    private MockRestServiceServer server;
    private G2bApiClient client;

    @BeforeEach
    void setUp() {
        G2bApiProperties properties = new G2bApiProperties();
        properties.setBaseUrl("https://apis.data.go.kr/1230000/ad/BidPublicInfoService");
        properties.setServiceKey("test+key/with/special==");
        properties.setOperation("getBidPblancListInfoServcPPSSrch");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new G2bApiClient(builder.build(), properties, JsonMapper.builder().build());
    }

    @AfterEach
    void tearDown() {
        server.verify();
    }

    @Test
    void searchBids_parsesSampleResponse() {
        String sampleJson = """
                {
                  "response": {
                    "header": {
                      "resultCode": "00",
                      "resultMsg": "NORMAL SERVICE."
                    },
                    "body": {
                      "items": [
                        {
                          "bidNtceNo": "20250701001",
                          "bidNtceNm": "소프트웨어 개발 용역",
                          "bidNtceDt": "2026-07-01 10:00:00",
                          "cntrctCnclsMthdNm": "일반경쟁",
                          "ntceInsttNm": "테스트기관",
                          "presmptPrce": "1000000",
                          "VAT": "100000",
                          "pubPrcrmntClsfcNm": "컴퓨터시스템 통합 서비스",
                          "srvceDivNm": "일반용역"
                        }
                      ],
                      "numOfRows": 100,
                      "pageNo": 1,
                      "totalCount": 1
                    }
                  }
                }
                """;

        server.expect(requestTo(org.hamcrest.Matchers.containsString("serviceKey=test%2Bkey%2Fwith%2Fspecial%3D%3D")))
                .andRespond(withSuccess(sampleJson, org.springframework.http.MediaType.APPLICATION_JSON));

        var result = client.searchBids(
                "",
                null,
                "소프트웨어사업자(컴퓨터관련서비스업)",
                "1468",
                "전체",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 5),
                null,
                null,
                false,
                1,
                1,
                100
        );

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(result.getPageNo()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(100);
    }

    @Test
    void searchBids_filtersOutNonGeneralOrTechnicalServiceDivisions() {
        String sampleJson = """
                {
                  "response": {
                    "header": {
                      "resultCode": "00",
                      "resultMsg": "NORMAL SERVICE."
                    },
                    "body": {
                      "items": [
                        {
                          "bidNtceNo": "20250701001",
                          "bidNtceNm": "일반용역 공고",
                          "bidNtceDt": "2026-07-01 10:00:00",
                          "srvceDivNm": "일반용역"
                        },
                        {
                          "bidNtceNo": "20250701002",
                          "bidNtceNm": "기술용역 공고",
                          "bidNtceDt": "2026-07-01 11:00:00",
                          "srvceDivNm": "기술용역"
                        },
                        {
                          "bidNtceNo": "20250701003",
                          "bidNtceNm": "제외 대상",
                          "bidNtceDt": "2026-07-01 12:00:00",
                          "srvceDivNm": "기타용역"
                        }
                      ],
                      "numOfRows": 100,
                      "pageNo": 1,
                      "totalCount": 3
                    }
                  }
                }
                """;

        server.expect(requestTo(org.hamcrest.Matchers.containsString("serviceKey=test%2Bkey%2Fwith%2Fspecial%3D%3D")))
                .andRespond(withSuccess(sampleJson, org.springframework.http.MediaType.APPLICATION_JSON));

        var result = client.searchBids(
                "",
                null,
                "",
                "",
                "전체",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 5),
                null,
                null,
                false,
                1,
                1,
                100
        );

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems())
                .extracting("bidNtceNo")
                .containsExactly("20250701002", "20250701001");
    }

    @Test
    void searchBids_sortsByBidNtceDtDescending() {
        String sampleJson = """
                {
                  "response": {
                    "header": {
                      "resultCode": "00",
                      "resultMsg": "NORMAL SERVICE."
                    },
                    "body": {
                      "items": [
                        {
                          "bidNtceNo": "20250701001",
                          "bidNtceNm": "오전 공고",
                          "bidNtceDt": "2026-07-01 10:00:00",
                          "srvceDivNm": "일반용역"
                        },
                        {
                          "bidNtceNo": "20250701003",
                          "bidNtceNm": "정오 공고",
                          "bidNtceDt": "2026-07-01 12:00:00",
                          "srvceDivNm": "일반용역"
                        },
                        {
                          "bidNtceNo": "20250701002",
                          "bidNtceNm": "11시 공고",
                          "bidNtceDt": "2026-07-01 11:00:00",
                          "srvceDivNm": "일반용역"
                        }
                      ],
                      "numOfRows": 100,
                      "pageNo": 1,
                      "totalCount": 3
                    }
                  }
                }
                """;

        server.expect(requestTo(org.hamcrest.Matchers.containsString("serviceKey=test%2Bkey%2Fwith%2Fspecial%3D%3D")))
                .andRespond(withSuccess(sampleJson, org.springframework.http.MediaType.APPLICATION_JSON));

        var result = client.searchBids(
                "",
                null,
                "",
                "",
                "전체",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 5),
                null,
                null,
                false,
                1,
                1,
                100
        );

        assertThat(result.getItems())
                .extracting("bidNtceNo")
                .containsExactly("20250701003", "20250701002", "20250701001");
    }

    @Test
    void getBidDetail_formatsRegionRestrictionFromParticipationRegionApi() {
        String searchJson = """
                {
                  "response": {
                    "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
                    "body": {
                      "items": {
                        "bidNtceNo": "R26BK01527855",
                        "bidNtceOrd": "000",
                        "bidNtceNm": "지역제한 테스트",
                        "bidNtceDt": "2026-06-10 10:00:00",
                        "bidPrtcptLmtYn": "Y",
                        "srvceDivNm": "일반용역"
                      },
                      "numOfRows": 100,
                      "pageNo": 1,
                      "totalCount": 1
                    }
                  }
                }
                """;
        String regionJson = """
                {
                  "response": {
                    "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
                    "body": {
                      "items": {
                        "prtcptPsblRgnNm": "경상북도",
                        "prtcptPsblRgnExpln": "본 공고에 단독으로 입찰 참여하는 경우, 입찰 참여자의 주된 영업소재지(법인인 경우 본사 소재지)가 참가가능지역에 있어야 합니다."
                      },
                      "totalCount": 1
                    }
                  }
                }
                """;

        server.expect(requestTo(org.hamcrest.Matchers.containsString("getBidPblancListInfoServcPPSSrch")))
                .andRespond(withSuccess(searchJson, org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("getBidPblancListInfoPrtcptPsblRgn")))
                .andRespond(withSuccess(regionJson, org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("getBidPblancListInfoBidPrcPsblIndstrytyServc")))
                .andRespond(withSuccess(emptyItemsJson(), org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("getBidPblancListInfoBidPrcPsblIndstryty")))
                .andRespond(withSuccess(emptyItemsJson(), org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("getBidPblancListInfoEorderAtchFileInfo")))
                .andRespond(withSuccess(emptyItemsJson(), org.springframework.http.MediaType.APPLICATION_JSON));

        var detail = client.getBidDetail(
                "R26BK01527855",
                "000",
                LocalDate.of(2026, 6, 10),
                "1468",
                "소프트웨어사업자(컴퓨터관련서비스사업)"
        );

        assertThat(detail.getRegionRestriction()).isEqualTo("""
                [경상북도]
                ㆍ본 공고에 단독으로 입찰 참여하는 경우, 입찰 참여자의 주된 영업소재지(법인인 경우 본사 소재지)가 참가가능지역에 있어야 합니다.""");
    }
}
