package com.nara.nara_be.service.impl;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.nara.nara_be.config.GoogleDriveProperties;
import com.nara.nara_be.dto.GoogleDriveBreadcrumbItem;
import com.nara.nara_be.dto.GoogleDriveCreateFolderRequest;
import com.nara.nara_be.dto.GoogleDriveFileResponse;
import com.nara.nara_be.dto.GoogleDriveListResponse;
import com.nara.nara_be.dto.GoogleDriveStatusResponse;
import com.nara.nara_be.dto.GoogleDriveUpdateFileRequest;
import com.nara.nara_be.exception.BusinessException;
import com.nara.nara_be.service.GoogleDriveService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleDriveServiceImpl implements GoogleDriveService {

    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
    private static final String FILE_FIELDS = "id,name,mimeType,size,modifiedTime,parents,webViewLink";

    private final GoogleDriveProperties properties;

    private Drive drive;
    private boolean available;

    @PostConstruct
    void init() {
        if (!StringUtils.hasText(properties.getCredentialsPath())) {
            log.warn("Google Drive credentials path is not configured (app.google-drive.credentials-path)");
            return;
        }
        if (!StringUtils.hasText(properties.getSharedDriveId())) {
            log.warn("Google Drive shared drive ID is not configured (app.google-drive.shared-drive-id)");
            return;
        }

        Path credentialsPath = Path.of(properties.getCredentialsPath().trim());
        if (!Files.isRegularFile(credentialsPath)) {
            log.warn("Google Drive credentials file not found: {}", credentialsPath.toAbsolutePath());
            return;
        }

        try (InputStream inputStream = Files.newInputStream(credentialsPath)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(inputStream)
                    .createScoped(Collections.singleton(DriveScopes.DRIVE));

            drive = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials)
            )
                    .setApplicationName(properties.getApplicationName())
                    .build();
            available = true;
            log.info("Google Drive client initialized for shared drive {}", properties.getSharedDriveId());
        } catch (Exception e) {
            log.error("Failed to initialize Google Drive client", e);
        }
    }

    @Override
    public GoogleDriveStatusResponse getStatus() {
        return GoogleDriveStatusResponse.builder()
                .available(available)
                .sharedDriveConfigured(StringUtils.hasText(properties.getSharedDriveId()))
                .sharedDriveId(properties.getSharedDriveId())
                .rootFolderId(properties.getSharedDriveId())
                .build();
    }

    @Override
    public GoogleDriveListResponse listFiles(String folderId) {
        Drive client = requireDrive();
        String currentFolderId = resolveFolderId(folderId);

        File currentFolder = getDriveFile(client, currentFolderId);
        String parentFolderId = resolveParentFolderId(currentFolder, currentFolderId);

        FileList fileList = executeList(client, currentFolderId);
        List<GoogleDriveFileResponse> files = fileList.getFiles() == null
                ? List.of()
                : fileList.getFiles().stream()
                .map(this::toResponse)
                .sorted(Comparator
                        .comparing(GoogleDriveFileResponse::isFolder).reversed()
                        .thenComparing(file -> file.getName().toLowerCase()))
                .toList();

        return GoogleDriveListResponse.builder()
                .currentFolderId(currentFolderId)
                .currentFolderName(currentFolder.getName())
                .parentFolderId(parentFolderId)
                .breadcrumbs(buildBreadcrumbs(client, currentFolderId))
                .files(files)
                .build();
    }

    @Override
    public GoogleDriveFileResponse createFolder(GoogleDriveCreateFolderRequest request) {
        Drive client = requireDrive();
        validateName(request.getName());

        String parentId = resolveFolderId(request.getParentId());
        File metadata = new File();
        metadata.setName(request.getName().trim());
        metadata.setMimeType(FOLDER_MIME_TYPE);
        metadata.setParents(List.of(parentId));

        try {
            File created = client.files().create(metadata)
                    .setSupportsAllDrives(true)
                    .setFields(FILE_FIELDS)
                    .execute();
            return toResponse(created);
        } catch (IOException e) {
            throw apiException("폴더를 만들지 못했습니다.", e);
        }
    }

    @Override
    public GoogleDriveFileResponse uploadFile(String parentId, MultipartFile file) {
        Drive client = requireDrive();
        if (file == null || file.isEmpty()) {
            throw new BusinessException("업로드할 파일을 선택해 주세요.", HttpStatus.BAD_REQUEST);
        }

        String resolvedParentId = resolveFolderId(parentId);
        String originalName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().trim()
                : "untitled";

        File metadata = new File();
        metadata.setName(originalName);
        metadata.setParents(List.of(resolvedParentId));

        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : "application/octet-stream";

        try (InputStream inputStream = file.getInputStream()) {
            InputStreamContent mediaContent = new InputStreamContent(contentType, inputStream);
            if (file.getSize() >= 0) {
                mediaContent.setLength(file.getSize());
            }

            File uploaded = client.files().create(metadata, mediaContent)
                    .setSupportsAllDrives(true)
                    .setFields(FILE_FIELDS)
                    .execute();
            return toResponse(uploaded);
        } catch (IOException e) {
            throw apiException("파일을 업로드하지 못했습니다.", e);
        }
    }

    @Override
    public Resource downloadFile(String fileId) {
        Drive client = requireDrive();
        validateFileId(fileId);

        File metadata = getDriveFile(client, fileId);
        if (FOLDER_MIME_TYPE.equals(metadata.getMimeType())) {
            throw new BusinessException("폴더는 다운로드할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            client.files().get(fileId)
                    .setSupportsAllDrives(true)
                    .executeMediaAndDownloadTo(outputStream);
            return new ByteArrayResource(outputStream.toByteArray()) {
                @Override
                public String getFilename() {
                    return metadata.getName();
                }
            };
        } catch (IOException e) {
            throw apiException("파일을 다운로드하지 못했습니다.", e);
        }
    }

    @Override
    public GoogleDriveFileResponse getFileMetadata(String fileId) {
        Drive client = requireDrive();
        validateFileId(fileId);
        return toResponse(getDriveFile(client, fileId));
    }

    @Override
    public GoogleDriveFileResponse updateFile(String fileId, GoogleDriveUpdateFileRequest request) {
        Drive client = requireDrive();
        validateFileId(fileId);

        File metadata = new File();
        boolean hasUpdate = false;

        if (StringUtils.hasText(request.getName())) {
            metadata.setName(request.getName().trim());
            hasUpdate = true;
        }

        if (!hasUpdate && !StringUtils.hasText(request.getParentId())) {
            throw new BusinessException("변경할 내용이 없습니다.", HttpStatus.BAD_REQUEST);
        }

        try {
            File updated;
            if (hasUpdate) {
                updated = client.files().update(fileId, metadata)
                        .setSupportsAllDrives(true)
                        .setFields(FILE_FIELDS)
                        .execute();
            } else {
                updated = getDriveFile(client, fileId);
            }

            if (StringUtils.hasText(request.getParentId())) {
                moveFile(client, fileId, resolveFolderId(request.getParentId()));
                updated = getDriveFile(client, fileId);
            }

            return toResponse(updated);
        } catch (IOException e) {
            throw apiException("파일 정보를 수정하지 못했습니다.", e);
        }
    }

    @Override
    public void deleteFile(String fileId) {
        Drive client = requireDrive();
        validateFileId(fileId);

        try {
            client.files().delete(fileId)
                    .setSupportsAllDrives(true)
                    .execute();
        } catch (IOException e) {
            throw apiException("파일을 삭제하지 못했습니다.", e);
        }
    }

    @Override
    public GoogleDriveFileResponse findFolderByName(String parentFolderId, String folderName) {
        if (!available || drive == null) {
            return null;
        }
        Drive client = requireDrive();
        return findChildFolder(client, resolveFolderId(parentFolderId), sanitizeDriveName(folderName));
    }

    @Override
    public void deleteFolderRecursively(String folderId) {
        Drive client = requireDrive();
        validateFileId(folderId);

        try {
            deleteFolderContents(client, folderId);
            client.files().delete(folderId)
                    .setSupportsAllDrives(true)
                    .execute();
        } catch (IOException e) {
            throw apiException("폴더를 삭제하지 못했습니다.", e);
        }
    }

    @Override
    public void deleteFavoriteFolder(String storedFolderId, String parentFolderId, String folderName) {
        if (!available || drive == null) {
            return;
        }

        String folderId = StringUtils.hasText(storedFolderId) ? storedFolderId.trim() : null;
        if (!StringUtils.hasText(folderId)) {
            GoogleDriveFileResponse folder = findFolderByName(parentFolderId, folderName);
            if (folder == null) {
                return;
            }
            folderId = folder.getId();
        }

        deleteFolderRecursively(folderId);
    }

    private void deleteFolderContents(Drive client, String folderId) throws IOException {
        String pageToken = null;
        do {
            FileList fileList = client.files().list()
                    .setQ("'" + folderId + "' in parents and trashed = false")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, mimeType)")
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true)
                    .setPageToken(pageToken)
                    .execute();

            if (fileList.getFiles() != null) {
                for (File child : fileList.getFiles()) {
                    if (FOLDER_MIME_TYPE.equals(child.getMimeType())) {
                        deleteFolderRecursively(child.getId());
                    } else {
                        client.files().delete(child.getId())
                                .setSupportsAllDrives(true)
                                .execute();
                    }
                }
            }
            pageToken = fileList.getNextPageToken();
        } while (StringUtils.hasText(pageToken));
    }

    @Override
    public GoogleDriveFileResponse findOrCreateFolder(String parentFolderId, String folderName) {
        Drive client = requireDrive();
        String resolvedParentId = resolveFolderId(parentFolderId);
        String sanitizedName = sanitizeDriveName(folderName);

        GoogleDriveFileResponse existing = findChildFolder(client, resolvedParentId, sanitizedName);
        if (existing != null) {
            return existing;
        }

        GoogleDriveCreateFolderRequest request = new GoogleDriveCreateFolderRequest();
        request.setName(sanitizedName);
        request.setParentId(resolvedParentId);
        return createFolder(request);
    }

    @Override
    public GoogleDriveFileResponse uploadLocalFile(
            String parentId,
            Path filePath,
            String fileName,
            String contentType
    ) {
        Drive client = requireDrive();
        if (filePath == null || !Files.isRegularFile(filePath)) {
            throw new BusinessException("업로드할 파일이 없습니다.", HttpStatus.BAD_REQUEST);
        }

        String resolvedParentId = resolveFolderId(parentId);
        String uploadName = sanitizeDriveName(StringUtils.hasText(fileName) ? fileName : filePath.getFileName().toString());
        String resolvedContentType = StringUtils.hasText(contentType) ? contentType : "application/octet-stream";

        File metadata = new File();
        metadata.setName(uploadName);
        metadata.setParents(List.of(resolvedParentId));

        try {
            FileContent mediaContent = new FileContent(resolvedContentType, filePath.toFile());
            File uploaded = client.files().create(metadata, mediaContent)
                    .setSupportsAllDrives(true)
                    .setFields(FILE_FIELDS)
                    .execute();
            return toResponse(uploaded);
        } catch (IOException e) {
            throw apiException("파일을 업로드하지 못했습니다.", e);
        }
    }

    private GoogleDriveFileResponse findChildFolder(Drive client, String parentFolderId, String folderName) {
        String escapedName = folderName.replace("'", "\\'");
        String query = "'" + parentFolderId + "' in parents"
                + " and mimeType = '" + FOLDER_MIME_TYPE + "'"
                + " and name = '" + escapedName + "'"
                + " and trashed = false";

        try {
            FileList fileList = client.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("files(" + FILE_FIELDS + ")")
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true)
                    .setPageSize(1)
                    .execute();

            if (fileList.getFiles() == null || fileList.getFiles().isEmpty()) {
                return null;
            }
            return toResponse(fileList.getFiles().get(0));
        } catch (IOException e) {
            throw apiException("폴더를 찾지 못했습니다.", e);
        }
    }

    private String sanitizeDriveName(String name) {
        if (!StringUtils.hasText(name)) {
            return "미명";
        }
        return name.trim()
                .replace("/", "-")
                .replace("\\", "-")
                .replaceAll("[\\x00-\\x1f]", "");
    }

    private Drive requireDrive() {
        if (!available || drive == null) {
            throw new BusinessException(
                    "Google Drive가 설정되지 않았습니다. credentials-path와 shared-drive-id를 확인해 주세요.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
        return drive;
    }

    private String resolveFolderId(String folderId) {
        if (StringUtils.hasText(folderId)) {
            return folderId.trim();
        }
        if (!StringUtils.hasText(properties.getSharedDriveId())) {
            throw new BusinessException("공유 드라이브 ID가 설정되지 않았습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return properties.getSharedDriveId().trim();
    }

    private FileList executeList(Drive client, String folderId) {
        try {
            return client.files().list()
                    .setQ("'" + folderId + "' in parents and trashed = false")
                    .setSpaces("drive")
                    .setFields("files(" + FILE_FIELDS + ")")
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true)
                    .setOrderBy("folder,name_natural")
                    .execute();
        } catch (IOException e) {
            throw apiException("파일 목록을 불러오지 못했습니다.", e);
        }
    }

    private File getDriveFile(Drive client, String fileId) {
        try {
            return client.files().get(fileId)
                    .setSupportsAllDrives(true)
                    .setFields(FILE_FIELDS)
                    .execute();
        } catch (IOException e) {
            throw apiException("파일 정보를 불러오지 못했습니다.", e);
        }
    }

    private void moveFile(Drive client, String fileId, String newParentId) throws IOException {
        File current = getDriveFile(client, fileId);
        String previousParent = current.getParents() == null || current.getParents().isEmpty()
                ? null
                : current.getParents().get(0);

        if (newParentId.equals(previousParent)) {
            return;
        }

        client.files().update(fileId, null)
                .setSupportsAllDrives(true)
                .setAddParents(newParentId)
                .setRemoveParents(previousParent)
                .setFields(FILE_FIELDS)
                .execute();
    }

    private List<GoogleDriveBreadcrumbItem> buildBreadcrumbs(Drive client, String folderId) {
        List<GoogleDriveBreadcrumbItem> breadcrumbs = new ArrayList<>();
        String currentId = folderId;
        String sharedDriveId = properties.getSharedDriveId().trim();

        while (StringUtils.hasText(currentId)) {
            File file = getDriveFile(client, currentId);
            breadcrumbs.add(GoogleDriveBreadcrumbItem.builder()
                    .id(file.getId())
                    .name(file.getName())
                    .build());

            if (sharedDriveId.equals(currentId)) {
                break;
            }

            if (file.getParents() == null || file.getParents().isEmpty()) {
                break;
            }

            String parentId = file.getParents().get(0);
            if (parentId.equals(currentId)) {
                break;
            }
            currentId = parentId;
        }

        Collections.reverse(breadcrumbs);
        if (breadcrumbs.isEmpty() || !sharedDriveId.equals(breadcrumbs.get(0).getId())) {
            breadcrumbs.add(0, GoogleDriveBreadcrumbItem.builder()
                    .id(sharedDriveId)
                    .name("공유 드라이브")
                    .build());
        } else {
            breadcrumbs.set(0, GoogleDriveBreadcrumbItem.builder()
                    .id(sharedDriveId)
                    .name("공유 드라이브")
                    .build());
        }

        return breadcrumbs;
    }

    private String resolveParentFolderId(File currentFolder, String currentFolderId) {
        String sharedDriveId = properties.getSharedDriveId().trim();
        if (sharedDriveId.equals(currentFolderId)) {
            return null;
        }
        if (currentFolder.getParents() == null || currentFolder.getParents().isEmpty()) {
            return sharedDriveId;
        }
        return currentFolder.getParents().get(0);
    }

    private GoogleDriveFileResponse toResponse(File file) {
        String parentId = file.getParents() == null || file.getParents().isEmpty()
                ? null
                : file.getParents().get(0);

        return GoogleDriveFileResponse.builder()
                .id(file.getId())
                .name(file.getName())
                .mimeType(file.getMimeType())
                .size(file.getSize())
                .modifiedTime(file.getModifiedTime() == null ? null : file.getModifiedTime().toStringRfc3339())
                .folder(FOLDER_MIME_TYPE.equals(file.getMimeType()))
                .webViewLink(file.getWebViewLink())
                .parentId(parentId)
                .build();
    }

    private void validateName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("이름을 입력해 주세요.", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateFileId(String fileId) {
        if (!StringUtils.hasText(fileId)) {
            throw new BusinessException("파일 ID가 필요합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private BusinessException apiException(String message, IOException cause) {
        log.warn("{}: {}", message, cause.getMessage());
        return new BusinessException(message + " (" + cause.getMessage() + ")", HttpStatus.BAD_GATEWAY);
    }
}
