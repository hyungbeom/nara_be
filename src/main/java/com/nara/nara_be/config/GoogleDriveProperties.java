package com.nara.nara_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.google-drive")
public class GoogleDriveProperties {

    /** 서비스 계정 JSON 키 파일 경로 */
    private String credentialsPath = "";

    /** 공유 드라이브 ID (Shared Drive) */
    private String sharedDriveId = "";

    private String applicationName = "nara-be";
}
