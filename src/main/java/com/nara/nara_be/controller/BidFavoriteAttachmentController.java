package com.nara.nara_be.controller;

import com.nara.nara_be.common.response.ApiResponse;
import com.nara.nara_be.dao.UserDao;
import com.nara.nara_be.domain.BidFavoriteAttachment;
import com.nara.nara_be.domain.User;
import com.nara.nara_be.dto.BidFavoriteAttachmentResponse;
import com.nara.nara_be.exception.BusinessException;
import com.nara.nara_be.service.BidFavoriteAttachmentService;
import com.nara.nara_be.service.BidFavoriteGoogleDriveUploadAsyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/bids/favorites/attachments")
@RequiredArgsConstructor
public class BidFavoriteAttachmentController {

    private final BidFavoriteAttachmentService bidFavoriteAttachmentService;
    private final BidFavoriteGoogleDriveUploadAsyncService googleDriveUploadAsyncService;
    private final UserDao userDao;

    @GetMapping("/{attachmentSeq}/download")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long attachmentSeq
    ) {
        Long userSeq = requireUserSeq(userDetails);
        BidFavoriteAttachment attachment = bidFavoriteAttachmentService.findAttachmentForUser(attachmentSeq, userSeq);
        Resource resource = bidFavoriteAttachmentService.loadAttachmentFile(attachmentSeq, userSeq);

        String fileName = attachment.getStoredFileName();
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (attachment.getContentType() != null) {
            mediaType = MediaType.parseMediaType(attachment.getContentType());
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileName, StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(resource);
    }

    @PostMapping("/{attachmentSeq}/convert-pdf")
    public ApiResponse<BidFavoriteAttachmentResponse> convertToPdf(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long attachmentSeq
    ) {
        BidFavoriteAttachmentResponse converted = bidFavoriteAttachmentService.convertToPdf(
                attachmentSeq,
                requireUserSeq(userDetails)
        );
        return ApiResponse.success("PDF로 변환해 저장했습니다.", converted);
    }

    @PostMapping("/{attachmentSeq}/upload-google-drive")
    public ResponseEntity<ApiResponse<Void>> uploadToGoogleDrive(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long attachmentSeq
    ) {
        Long userSeq = requireUserSeq(userDetails);
        bidFavoriteAttachmentService.enqueueGoogleDriveUpload(attachmentSeq, userSeq);
        googleDriveUploadAsyncService.upload(attachmentSeq, userSeq);
        return ResponseEntity.accepted()
                .body(ApiResponse.success("구글 드라이브 저장을 시작했습니다.", null));
    }

    private Long requireUserSeq(UserDetails userDetails) {
        if (userDetails == null) {
            throw new BusinessException("인증이 필요합니다.", HttpStatus.UNAUTHORIZED);
        }
        User user = userDao.findByUserId(userDetails.getUsername());
        if (user == null) {
            throw new BusinessException("존재하지 않는 사용자입니다.", HttpStatus.UNAUTHORIZED);
        }
        return user.getUserSeq();
    }
}
