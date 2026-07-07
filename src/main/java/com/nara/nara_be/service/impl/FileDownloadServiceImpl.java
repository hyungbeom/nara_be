package com.nara.nara_be.service.impl;

import com.nara.nara_be.config.StorageProperties;
import com.nara.nara_be.service.FileDownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileDownloadServiceImpl implements FileDownloadService {

    private final RestClient fileDownloadRestClient;
    private final StorageProperties storageProperties;

    @Override
    public DownloadedFile download(String fileUrl, String fileName) {
        if (!StringUtils.hasText(fileUrl)) {
            throw new IllegalArgumentException("첨부파일 URL이 비어 있습니다.");
        }

        String resolvedName = StringUtils.hasText(fileName) ? fileName.trim() : "attachment";
        Path tempDir = resolveTempDir();

        try {
            Files.createDirectories(tempDir);
            Path targetPath = tempDir.resolve(UUID.randomUUID() + "_" + sanitizeFileName(resolvedName));

            ResponseEntity<byte[]> response = fileDownloadRestClient.get()
                    .uri(URI.create(fileUrl.trim()))
                    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; NaraBe/1.0)")
                    .header(HttpHeaders.REFERER, "https://www.g2b.go.kr/")
                    .retrieve()
                    .toEntity(byte[].class);

            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                throw new IllegalStateException("첨부파일 내용이 비어 있습니다.");
            }

            Files.write(targetPath, body);
            String contentType = resolveContentType(response.getHeaders(), resolvedName);
            return new DownloadedFile(targetPath, contentType, body.length);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("첨부파일 다운로드에 실패했습니다: " + resolvedName, ex);
        }
    }

    private Path resolveTempDir() {
        return Path.of(storageProperties.getBasePath(), "tmp", "downloads");
    }

    private String resolveContentType(HttpHeaders headers, String fileName) {
        MediaType mediaType = headers.getContentType();
        if (mediaType != null) {
            return mediaType.toString();
        }

        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".hwpx")) {
            return "application/hwp+zip";
        }
        if (lower.endsWith(".hwp")) {
            return "application/x-hwp";
        }
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lower.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (lower.endsWith(".zip")) {
            return "application/zip";
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    static String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("임시 파일 삭제 실패: {}", path, ex);
        }
    }

    static void copyFile(Path source, Path target) {
        try {
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = Files.newInputStream(source)) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("파일 복사에 실패했습니다.", ex);
        }
    }
}
