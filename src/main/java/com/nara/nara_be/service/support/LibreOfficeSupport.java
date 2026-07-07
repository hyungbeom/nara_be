package com.nara.nara_be.service.support;

import com.nara.nara_be.config.StorageProperties;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LibreOfficeSupport {

    private LibreOfficeSupport() {
    }

    public static String resolveLibreOfficePath(StorageProperties storageProperties) {
        if (StringUtils.hasText(storageProperties.getLibreOfficePath())) {
            Path configured = Path.of(storageProperties.getLibreOfficePath());
            if (Files.exists(configured)) {
                return configured.toAbsolutePath().toString();
            }
        }

        List<Path> candidates = new ArrayList<>();
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        if (StringUtils.hasText(programFiles)) {
            candidates.add(Path.of(programFiles, "LibreOffice", "program", "soffice.exe"));
        }
        if (StringUtils.hasText(programFilesX86)) {
            candidates.add(Path.of(programFilesX86, "LibreOffice", "program", "soffice.exe"));
        }
        candidates.add(Path.of("/usr/bin/libreoffice"));
        candidates.add(Path.of("/usr/bin/soffice"));

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate.toAbsolutePath().toString();
            }
        }
        return null;
    }

    public static String toFileUri(Path path) {
        String normalized = path.toAbsolutePath().toString().replace('\\', '/');
        if (normalized.startsWith("//")) {
            return "file:" + normalized;
        }
        return "file:///" + normalized;
    }
}
