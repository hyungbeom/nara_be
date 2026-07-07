package com.nara.nara_be.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 나라장터 검색 일자 구분.
 * <ul>
 *   <li>{@code pbancDt} → Open API {@code inqryDiv=1} (공고게시일시)</li>
 *   <li>{@code opengDt} → Open API {@code inqryDiv=2} (개찰일시)</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum BidDateQueryType {

    ANNOUNCEMENT("pbancDt", 1),
    OPENING("opengDt", 2);

    private final String bizYmdDiv;
    private final int inqryDiv;
}
