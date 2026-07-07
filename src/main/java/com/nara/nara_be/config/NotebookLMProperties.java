package com.nara.nara_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.notebooklm")
public class NotebookLMProperties {

    private boolean enabled = true;

    /** notebooklm-py가 설치된 Python 실행 파일 */
    private String pythonExecutable = "";

    /** NOTEBOOKLM_HOME (master_token.json, storage_state.json 저장 위치) */
    private String homePath = "./storage/notebooklm";

    /** 일반 CLI 명령 타임아웃(초) */
    private int commandTimeoutSeconds = 120;

    /** Deep Research 타임아웃(초, add-research --timeout) */
    private int researchTimeoutSeconds = 1800;

    /** ask 명령 타임아웃(초) */
    private int askTimeoutSeconds = 900;

    /** 브라우저 로그인 대기 시간(초) */
    private int loginTimeoutSeconds = 300;
}
