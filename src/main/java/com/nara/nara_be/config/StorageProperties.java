package com.nara.nara_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private String basePath = "./storage";
    private String libreOfficePath = "";
    private int downloadTimeoutSeconds = 120;
    private int conversionTimeoutSeconds = 180;

    public Path getLibreOfficeProfileDir() {
        return Path.of(basePath, "libreoffice", "profile");
    }
}
