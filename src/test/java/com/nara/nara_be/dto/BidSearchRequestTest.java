package com.nara.nara_be.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class BidSearchRequestTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void deserializesWrappedDlSrchParamM() throws Exception {
        String json = """
                {
                  "dlSrchParamM": {
                    "currentPage": 1,
                    "recordCountPerPage": "100",
                    "startBizYmd": "20260607",
                    "endBizYmd": "20260708",
                    "mindCd": "1468",
                    "bizYmdDiv": "pbancDt"
                  }
                }
                """;

        BidSearchRequest request = jsonMapper.readValue(json, BidSearchRequest.class);

        assertThat(request.hasDlSrchParams()).isTrue();
        assertThat(request.getStartBizYmd()).isEqualTo("20260607");
        assertThat(request.getEndBizYmd()).isEqualTo("20260708");
        assertThat(request.getMindCd()).isEqualTo("1468");
    }

    @Test
    void deserializesFlatDlSrchParamM() throws Exception {
        String json = """
                {
                  "currentPage": 1,
                  "recordCountPerPage": "100",
                  "startBizYmd": "20260607",
                  "endBizYmd": "20260708",
                  "mindCd": "1468",
                  "bizYmdDiv": "pbancDt"
                }
                """;

        BidSearchRequest request = jsonMapper.readValue(json, BidSearchRequest.class);

        assertThat(request.hasDlSrchParams()).isTrue();
        assertThat(request.getStartBizYmd()).isEqualTo("20260607");
        assertThat(request.getMindCd()).isEqualTo("1468");
    }
}
