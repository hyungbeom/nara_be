package com.nara.nara_be.service.impl;

import com.nara.nara_be.config.GoogleDriveProperties;
import com.nara.nara_be.config.StorageProperties;
import com.nara.nara_be.dao.BidFavoriteAttachmentDao;
import com.nara.nara_be.dao.BidFavoriteDao;
import com.nara.nara_be.domain.BidFavorite;
import com.nara.nara_be.domain.BidFavoriteAttachment;
import com.nara.nara_be.dto.BidAttachmentResponse;
import com.nara.nara_be.dto.BidFavoriteAttachmentResponse;
import com.nara.nara_be.dto.GoogleDriveFileResponse;
import com.nara.nara_be.dto.GoogleDriveUploadAttachmentResponse;
import com.nara.nara_be.exception.BusinessException;
import com.nara.nara_be.service.BidFavoriteAttachmentService;
import com.nara.nara_be.service.DocumentConversionService;
import com.nara.nara_be.service.FileDownloadService;
import com.nara.nara_be.service.GoogleDriveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidFavoriteAttachmentServiceImpl implements BidFavoriteAttachmentService {

    private final BidFavoriteAttachmentDao bidFavoriteAttachmentDao;
    private final BidFavoriteDao bidFavoriteDao;
    private final FileDownloadService fileDownloadService;
    private final DocumentConversionService documentConversionService;
    private final GoogleDriveService googleDriveService;
    private final GoogleDriveProperties googleDriveProperties;
    private final StorageProperties storageProperties;

    @Override
    public void saveAttachments(Long favoriteSeq, List<BidAttachmentResponse> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }

        Path favoriteDir = resolveFavoriteDir(favoriteSeq);
        try {
            Files.createDirectories(favoriteDir);
        } catch (IOException ex) {
            throw new IllegalStateException("첨부파일 저장 폴더를 만들 수 없습니다.", ex);
        }

        for (BidAttachmentResponse attachment : attachments) {
            saveSingleAttachment(favoriteSeq, favoriteDir, attachment);
        }
    }

    @Override
    public void deleteAttachments(Long favoriteSeq) {
        bidFavoriteAttachmentDao.deleteByFavoriteSeq(favoriteSeq);
        deleteDirectoryQuietly(resolveFavoriteDir(favoriteSeq));
    }

    @Override
    public Resource loadAttachmentFile(Long attachmentSeq, Long userSeq) {
        BidFavoriteAttachment attachment = bidFavoriteAttachmentDao.findOwnedAttachment(attachmentSeq, userSeq);
        if (attachment == null) {
            throw new BusinessException("첨부파일을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        Path filePath = Path.of(storageProperties.getBasePath()).resolve(attachment.getStoragePath());
        if (!Files.exists(filePath)) {
            throw new BusinessException("저장된 첨부파일이 없습니다.", HttpStatus.NOT_FOUND);
        }

        return new FileSystemResource(filePath);
    }

    @Override
    public BidFavoriteAttachment findAttachmentForUser(Long attachmentSeq, Long userSeq) {
        BidFavoriteAttachment attachment = bidFavoriteAttachmentDao.findOwnedAttachment(attachmentSeq, userSeq);
        if (attachment == null) {
            throw new BusinessException("첨부파일을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
        return attachment;
    }

    @Override
    public BidFavoriteAttachmentResponse convertToPdf(Long attachmentSeq, Long userSeq) {
        BidFavoriteAttachment source = findAttachmentForUser(attachmentSeq, userSeq);
        if (source.isConvertedFromHwpx()) {
            throw new BusinessException("이미 PDF 변환본입니다.", HttpStatus.BAD_REQUEST);
        }
        if (!isHancomDocument(source.getStoredFileName()) && !isHancomDocument(source.getOriginalFileName())) {
            throw new BusinessException("한글 문서만 PDF로 변환할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }

        BidFavoriteAttachment existingPdf = bidFavoriteAttachmentDao.findConvertedPdf(
                source.getFavoriteSeq(),
                source.getOriginalFileName()
        );
        if (existingPdf != null) {
            return toResponse(existingPdf);
        }

        Path sourcePath = Path.of(storageProperties.getBasePath()).resolve(source.getStoragePath());
        if (!Files.exists(sourcePath)) {
            throw new BusinessException("저장된 첨부파일이 없습니다.", HttpStatus.NOT_FOUND);
        }

        Path favoriteDir = resolveFavoriteDir(source.getFavoriteSeq());
        Path storedPdfFile = null;
        Path tempPdfFile = null;

        try {
            tempPdfFile = documentConversionService.convertHancomDocumentToPdf(sourcePath);
            String pdfFileName = replaceExtension(source.getOriginalFileName(), "pdf");
            storedPdfFile = favoriteDir.resolve(buildStoredFileName(pdfFileName));
            FileDownloadServiceImpl.copyFile(tempPdfFile, storedPdfFile);
            long pdfSize = Files.size(storedPdfFile);

            insertAttachmentRecord(
                    source.getFavoriteSeq(),
                    pdfFileName,
                    source.getOriginalFileName(),
                    source.getOriginalUrl(),
                    MediaType.APPLICATION_PDF_VALUE,
                    pdfSize,
                    true,
                    storedPdfFile
            );

            BidFavoriteAttachment saved = bidFavoriteAttachmentDao.findConvertedPdf(
                    source.getFavoriteSeq(),
                    source.getOriginalFileName()
            );
            if (saved == null) {
                throw new BusinessException("PDF 저장 결과를 확인할 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return toResponse(saved);
        } catch (IOException ex) {
            FileDownloadServiceImpl.deleteQuietly(storedPdfFile);
            throw new BusinessException("PDF 저장에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (RuntimeException ex) {
            FileDownloadServiceImpl.deleteQuietly(storedPdfFile);
            throw new BusinessException(
                    ex.getMessage() != null ? ex.getMessage() : "PDF 변환에 실패했습니다.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        } finally {
            FileDownloadServiceImpl.deleteQuietly(tempPdfFile);
        }
    }

    @Override
    public GoogleDriveUploadAttachmentResponse uploadToGoogleDrive(Long attachmentSeq, Long userSeq) {
        BidFavoriteAttachment attachment = findAttachmentForUser(attachmentSeq, userSeq);
        if (StringUtils.hasText(attachment.getGoogleDriveFileId())) {
            throw new BusinessException("이미 구글 드라이브에 저장된 파일입니다.", HttpStatus.CONFLICT);
        }

        BidFavorite favorite = bidFavoriteDao.findOwnedByFavoriteSeq(attachment.getFavoriteSeq(), userSeq);
        if (favorite == null) {
            throw new BusinessException("즐겨찾기 공고를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        Path filePath = Path.of(storageProperties.getBasePath()).resolve(attachment.getStoragePath());
        if (!Files.exists(filePath)) {
            throw new BusinessException("저장된 첨부파일이 없습니다.", HttpStatus.NOT_FOUND);
        }

        String folderName = StringUtils.hasText(favorite.getBidName()) ? favorite.getBidName() : "미명";
        String uploadFileName = attachment.isConvertedFromHwpx()
                ? attachment.getStoredFileName()
                : (StringUtils.hasText(attachment.getOriginalFileName())
                ? attachment.getOriginalFileName()
                : attachment.getStoredFileName());

        String folderId = favorite.getGoogleDriveFolderId();
        GoogleDriveFileResponse folder;
        if (StringUtils.hasText(folderId)) {
            folder = GoogleDriveFileResponse.builder()
                    .id(folderId)
                    .name(folderName)
                    .folder(true)
                    .build();
        } else {
            folder = googleDriveService.findOrCreateFolder(
                    googleDriveProperties.getSharedDriveId(),
                    folderName
            );
            bidFavoriteDao.updateGoogleDriveFolderId(favorite.getFavoriteSeq(), folder.getId());
        }

        GoogleDriveFileResponse uploaded = googleDriveService.uploadLocalFile(
                folder.getId(),
                filePath,
                uploadFileName,
                attachment.getContentType()
        );

        bidFavoriteAttachmentDao.updateGoogleDriveFileId(attachmentSeq, uploaded.getId());

        return GoogleDriveUploadAttachmentResponse.builder()
                .folderId(folder.getId())
                .folderName(folder.getName())
                .file(uploaded)
                .build();
    }

    @Override
    public void enqueueGoogleDriveUpload(Long attachmentSeq, Long userSeq) {
        BidFavoriteAttachment attachment = findAttachmentForUser(attachmentSeq, userSeq);
        if (StringUtils.hasText(attachment.getGoogleDriveFileId())) {
            throw new BusinessException("이미 구글 드라이브에 저장된 파일입니다.", HttpStatus.CONFLICT);
        }
    }

    private BidFavoriteAttachmentResponse toResponse(BidFavoriteAttachment attachment) {
        return BidFavoriteAttachmentResponse.builder()
                .attachmentSeq(attachment.getAttachmentSeq())
                .fileName(attachment.getStoredFileName())
                .originalFileName(attachment.getOriginalFileName())
                .contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize())
                .convertedFromHwpx(attachment.isConvertedFromHwpx())
                .googleDriveFileId(attachment.getGoogleDriveFileId())
                .build();
    }

    private void saveSingleAttachment(Long favoriteSeq, Path favoriteDir, BidAttachmentResponse attachment) {
        if (!StringUtils.hasText(attachment.getFileUrl())) {
            return;
        }

        String originalFileName = StringUtils.hasText(attachment.getFileName())
                ? attachment.getFileName().trim()
                : "attachment";
        Path downloadedFile = null;
        Path storedOriginalFile = null;
        Path storedPdfFile = null;

        try {
            FileDownloadService.DownloadedFile downloaded = fileDownloadService.download(
                    attachment.getFileUrl(),
                    originalFileName
            );
            downloadedFile = downloaded.filePath();

            storedOriginalFile = favoriteDir.resolve(buildStoredFileName(originalFileName));
            FileDownloadServiceImpl.copyFile(downloadedFile, storedOriginalFile);
            insertAttachmentRecord(
                    favoriteSeq,
                    originalFileName,
                    originalFileName,
                    attachment.getFileUrl(),
                    downloaded.contentType(),
                    downloaded.fileSize(),
                    false,
                    storedOriginalFile
            );

            if (isHancomDocument(originalFileName)) {
                try {
                    Path pdfFile = documentConversionService.convertHancomDocumentToPdf(downloadedFile);
                    String pdfFileName = replaceExtension(originalFileName, "pdf");
                    storedPdfFile = favoriteDir.resolve(buildStoredFileName(pdfFileName));
                    FileDownloadServiceImpl.copyFile(pdfFile, storedPdfFile);
                    long pdfSize;
                    try {
                        pdfSize = Files.size(storedPdfFile);
                    } catch (IOException ex) {
                        throw new IllegalStateException("변환된 PDF 크기를 확인할 수 없습니다.", ex);
                    }
                    insertAttachmentRecord(
                            favoriteSeq,
                            pdfFileName,
                            originalFileName,
                            attachment.getFileUrl(),
                            MediaType.APPLICATION_PDF_VALUE,
                            pdfSize,
                            true,
                            storedPdfFile
                    );
                    FileDownloadServiceImpl.deleteQuietly(pdfFile);
                } catch (RuntimeException ex) {
                    log.warn("HWPX PDF 변환 실패, 원본 한글 파일만 저장: {}", originalFileName, ex);
                }
            }
        } catch (RuntimeException ex) {
            log.error("첨부파일 저장 실패: {}", originalFileName, ex);
            FileDownloadServiceImpl.deleteQuietly(storedOriginalFile);
            FileDownloadServiceImpl.deleteQuietly(storedPdfFile);
        } finally {
            FileDownloadServiceImpl.deleteQuietly(downloadedFile);
        }
    }

    private void insertAttachmentRecord(
            Long favoriteSeq,
            String storedFileName,
            String originalFileName,
            String originalUrl,
            String contentType,
            long fileSize,
            boolean convertedFromHwpx,
            Path storedFile
    ) {
        String storagePath = Path.of(
                "favorites",
                String.valueOf(favoriteSeq),
                storedFile.getFileName().toString()
        ).toString().replace('\\', '/');

        BidFavoriteAttachment favoriteAttachment = BidFavoriteAttachment.builder()
                .favoriteSeq(favoriteSeq)
                .originalFileName(originalFileName)
                .storedFileName(storedFileName)
                .originalUrl(originalUrl)
                .contentType(contentType)
                .fileSize(fileSize)
                .convertedFromHwpx(convertedFromHwpx)
                .storagePath(storagePath)
                .build();

        bidFavoriteAttachmentDao.insert(favoriteAttachment);
    }

    private Path resolveFavoriteDir(Long favoriteSeq) {
        return Path.of(storageProperties.getBasePath(), "favorites", String.valueOf(favoriteSeq));
    }

    private String buildStoredFileName(String fileName) {
        return System.currentTimeMillis() + "_" + FileDownloadServiceImpl.sanitizeFileName(fileName);
    }

    private boolean isHancomDocument(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".hwpx") || lower.endsWith(".hwp");
    }

    private String replaceExtension(String fileName, String extension) {
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        return baseName + "." + extension;
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> FileDownloadServiceImpl.deleteQuietly(path));
        } catch (IOException ex) {
            log.warn("첨부파일 폴더 삭제 실패: {}", directory, ex);
        }
    }
}
