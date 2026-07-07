package com.nara.nara_be.controller;

import com.nara.nara_be.common.response.ApiResponse;
import com.nara.nara_be.dto.GoogleDriveCreateFolderRequest;
import com.nara.nara_be.dto.GoogleDriveFileResponse;
import com.nara.nara_be.dto.GoogleDriveListResponse;
import com.nara.nara_be.dto.GoogleDriveStatusResponse;
import com.nara.nara_be.dto.GoogleDriveUpdateFileRequest;
import com.nara.nara_be.service.GoogleDriveService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/google-drive")
@RequiredArgsConstructor
public class GoogleDriveController {

    private final GoogleDriveService googleDriveService;

    @GetMapping("/status")
    public ApiResponse<GoogleDriveStatusResponse> getStatus() {
        return ApiResponse.success(googleDriveService.getStatus());
    }

    @GetMapping("/files")
    public ApiResponse<GoogleDriveListResponse> listFiles(
            @RequestParam(required = false) String folderId
    ) {
        return ApiResponse.success(googleDriveService.listFiles(folderId));
    }

    @PostMapping("/folders")
    public ApiResponse<GoogleDriveFileResponse> createFolder(@RequestBody GoogleDriveCreateFolderRequest request) {
        return ApiResponse.success("폴더를 만들었습니다.", googleDriveService.createFolder(request));
    }

    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<GoogleDriveFileResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("parentId") String parentId
    ) {
        return ApiResponse.success("파일을 업로드했습니다.", googleDriveService.uploadFile(parentId, file));
    }

    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileId) {
        Resource resource = googleDriveService.downloadFile(fileId);
        GoogleDriveFileResponse metadata = googleDriveService.getFileMetadata(fileId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(metadata.getName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(resource);
    }

    @PatchMapping("/files/{fileId}")
    public ApiResponse<GoogleDriveFileResponse> updateFile(
            @PathVariable String fileId,
            @RequestBody GoogleDriveUpdateFileRequest request
    ) {
        return ApiResponse.success("파일 정보를 수정했습니다.", googleDriveService.updateFile(fileId, request));
    }

    @DeleteMapping("/files/{fileId}")
    public ApiResponse<Void> deleteFile(@PathVariable String fileId) {
        googleDriveService.deleteFile(fileId);
        return ApiResponse.success("파일을 삭제했습니다.", null);
    }
}
