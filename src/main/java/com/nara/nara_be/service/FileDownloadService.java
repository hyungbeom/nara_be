package com.nara.nara_be.service;

import java.nio.file.Path;

public interface FileDownloadService {

    DownloadedFile download(String fileUrl, String fileName);

    record DownloadedFile(Path filePath, String contentType, long fileSize) {
    }
}
