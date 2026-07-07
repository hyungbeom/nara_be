package com.nara.nara_be.service;

import com.nara.nara_be.dto.GoogleDriveCreateFolderRequest;
import com.nara.nara_be.dto.GoogleDriveFileResponse;
import com.nara.nara_be.dto.GoogleDriveListResponse;
import com.nara.nara_be.dto.GoogleDriveStatusResponse;
import com.nara.nara_be.dto.GoogleDriveUpdateFileRequest;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface GoogleDriveService {

    GoogleDriveStatusResponse getStatus();

    GoogleDriveListResponse listFiles(String folderId);

    GoogleDriveFileResponse createFolder(GoogleDriveCreateFolderRequest request);

    GoogleDriveFileResponse uploadFile(String parentId, MultipartFile file);

    Resource downloadFile(String fileId);

    GoogleDriveFileResponse getFileMetadata(String fileId);

    GoogleDriveFileResponse updateFile(String fileId, GoogleDriveUpdateFileRequest request);

    void deleteFile(String fileId);

    GoogleDriveFileResponse findFolderByName(String parentFolderId, String folderName);

    void deleteFolderRecursively(String folderId);

    void deleteFavoriteFolder(String storedFolderId, String parentFolderId, String folderName);

    GoogleDriveFileResponse findOrCreateFolder(String parentFolderId, String folderName);

    GoogleDriveFileResponse uploadLocalFile(
            String parentId,
            java.nio.file.Path filePath,
            String fileName,
            String contentType
    );
}
