package com.nara.nara_be.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotebookLMLoginRequest {

    /** master-token 방식에 사용할 Google 계정 이메일 */
    private String accountEmail;
}
