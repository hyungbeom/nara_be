package com.nara.nara_be.config;

import com.nara.nara_be.service.support.LibreOfficeSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class LibreOfficeHancomSupportInitializer {

    private static final String H2O_RESTART_URL =
            "https://github.com/ebandal/H2Orestart/releases/download/v0.7.13/H2Orestart.oxt";
    private static final String INSTALLED_MARKER = ".h2orestart-installed";

    private final StorageProperties storageProperties;

    @PostConstruct
    void init() {
        String libreOfficePath = LibreOfficeSupport.resolveLibreOfficePath(storageProperties);
        if (!StringUtils.hasText(libreOfficePath)) {
            log.warn("LibreOffice가 없어 HWPX 변환용 H2Orestart 확장을 설치하지 않습니다.");
            return;
        }

        Path libreOfficeDir = storageProperties.getLibreOfficeProfileDir().getParent();
        Path profileDir = storageProperties.getLibreOfficeProfileDir();
        Path extensionFile = libreOfficeDir.resolve("H2Orestart.oxt");
        Path marker = profileDir.resolve(INSTALLED_MARKER);

        try {
            Files.createDirectories(profileDir);
            if (!Files.exists(extensionFile)) {
                downloadExtension(extensionFile);
            }
            if (!Files.exists(marker)) {
                installExtension(libreOfficePath, profileDir, extensionFile);
                Files.writeString(marker, "installed");
                log.info("LibreOffice H2Orestart 확장 설치 완료: {}", profileDir.toAbsolutePath());
            }
        } catch (Exception ex) {
            log.error("LibreOffice H2Orestart 확장 설치 실패", ex);
        }
    }

    private void downloadExtension(Path extensionFile) throws IOException {
        log.info("H2Orestart 확장 다운로드 중...");
        try (InputStream inputStream = URI.create(H2O_RESTART_URL).toURL().openStream()) {
            Files.copy(inputStream, extensionFile);
        }
    }

    private void installExtension(String libreOfficePath, Path profileDir, Path extensionFile)
            throws IOException, InterruptedException {
        Path unopkg = Path.of(libreOfficePath).getParent().resolve("unopkg.exe");
        if (!Files.exists(unopkg)) {
            throw new IllegalStateException("unopkg.exe를 찾을 수 없습니다: " + unopkg);
        }

        List<String> command = new ArrayList<>();
        command.add(unopkg.toAbsolutePath().toString());
        command.add("add");
        command.add("-env:UserInstallation=" + LibreOfficeSupport.toFileUri(profileDir));
        command.add(extensionFile.toAbsolutePath().toString());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("H2Orestart 확장 설치 시간 초과");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("H2Orestart 확장 설치 실패: " + output.trim());
        }
    }
}
