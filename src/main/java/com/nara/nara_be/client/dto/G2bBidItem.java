package com.nara.nara_be.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class G2bBidItem {

    private String bidNtceNo;
    private String bidNtceNm;
    private String bidNtceDt;
    private String cntrctCnclsMthdNm;
    private String ntceInsttNm;
    private String ntceInsttOfclNm;
    private String ntceInsttOfclTelNo;
    private String ntceInsttOfclEmailAdrs;
    private String dminsttNm;
    private String presmptPrce;

    @JsonProperty("VAT")
    private String vat;
    private String pubPrcrmntLrgClsfcNm;
    private String pubPrcrmntMidClsfcNm;
    private String pubPrcrmntClsfcNm;
    private String bidNtceDtlUrl;
    private String bidNtceOrd;
    private String bidNtceDtlCntnts;
    private String bidClseDt;
    private String bidBeginDt;
    private String bidQlfctRgstDt;
    private String opengDt;
    private String bdgtAmt;
    private String sucsfbidMthdNm;
    private String bidMethdNm;
    private String intrbidYn;
    private String srvceDivNm;
}
