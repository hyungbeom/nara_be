package com.nara.nara_be.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 나라장터 UI 검색 파라미터(dlSrchParamM)와 동일한 구조.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DlSrchParamM {

    private Integer currentPage = 1;
    private String recordCountPerPage = "100";
    private String srchSeCd;
    private String saveTime;
    private String untySrchSeCd;
    private String bidCtrtMthdCd;
    private String bizNm;
    private String bizNo;
    private String bizYmd;
    private String bizYmdDiv;
    private String ctrtTyCd;
    private String dmstUntyGrpNm;
    private String dmstUntyGrpNo;
    private String dtlsItemNm;
    private String dtlsItemNo;
    private String endBizYmd;
    private String endInptDt;
    private String endPbancPstgDt;
    private String endPbancSrchItm02;
    private String endPbancSrchItm03;
    private String frcpYn;
    private String laseYn;
    private String mindCd;
    private String mindNm;
    private String pbancInstUntyGrpNm;
    private String pbancInstUntyGrpNo;
    private String prcmBsneAreaCd;
    private String prcmMaagSeCd;
    private String rsrvYn;
    private String slpRDdlnExclYn;
    private String startBizYmd;
    private String startInptDt;
    private String startPbancPstgDt;
    private String startPbancSrchItm02;
    private String startPbancSrchItm03;

    private String ctrtSrchItm01;
    private String ctrtSrchItm02;
    private String ctrtSrchItm03;
    private String ctrtSrchItm04;
    private String ctrtSrchItm05;
    private String ctrtSrchItm06;
    private String ctrtSrchItm07;
    private String ctrtSrchItm08;
    private String ctrtSrchItm09;
    private String ctrtSrchItm10;
    private String ctrtSrchItm11;
    private String ctrtSrchItm12;
    private String ctrtSrchItm13;
    private String ctrtSrchItm14;
    private String ctrtSrchItm15;
    private String dmndSrchItm01;
    private String dmndSrchItm02;
    private String dmndSrchItm03;
    private String dmndSrchItm04;
    private String dmndSrchItm05;
    private String oderSrchItm01;
    private String pbancSrchItm01;
    private String pbancSrchItm02;
    private String pbancSrchItm04;
    private String pbancSrchItm05;
    private String pbancSrchItm06;
    private String pbancSrchItm07;
    private String pbancSrchItm08;
    private String pbancSrchItm09;
    private String pbancSrchItm10;
    private String pbancSrchItm11;
    private String pbancSrchItm12;
    private String pbancSrchItm13;
    private String pbancSrchItm14;
    private String pbancSrchItm15;
}
