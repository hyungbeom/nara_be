package com.nara.nara_be.service.impl;

import com.nara.nara_be.dao.BidFavoriteAttachmentDao;
import com.nara.nara_be.dao.BidFavoriteDao;
import com.nara.nara_be.dao.UserDao;
import com.nara.nara_be.domain.BidFavorite;
import com.nara.nara_be.domain.BidFavoriteAttachment;
import com.nara.nara_be.domain.User;
import com.nara.nara_be.dto.BidDetailResponse;
import com.nara.nara_be.dto.BidFavoriteCheckResponse;
import com.nara.nara_be.dto.BidFavoriteRequest;
import com.nara.nara_be.dto.BidFavoriteResponse;
import com.nara.nara_be.dto.GoogleDriveFileResponse;
import com.nara.nara_be.exception.BusinessException;
import com.nara.nara_be.config.GoogleDriveProperties;
import com.nara.nara_be.service.BidFavoriteAttachmentService;
import com.nara.nara_be.service.BidFavoriteService;
import com.nara.nara_be.service.BidService;
import com.nara.nara_be.service.GoogleDriveService;
import com.nara.nara_be.service.NotebookLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidFavoriteServiceImpl implements BidFavoriteService {

    private final BidFavoriteDao bidFavoriteDao;
    private final BidFavoriteAttachmentDao bidFavoriteAttachmentDao;
    private final UserDao userDao;
    private final BidService bidService;
    private final BidFavoriteAttachmentService bidFavoriteAttachmentService;
    private final GoogleDriveService googleDriveService;
    private final GoogleDriveProperties googleDriveProperties;
    private final NotebookLMService notebookLMService;

    @Override
    public List<BidFavoriteResponse> findAll(String userId) {
        User user = requireUser(userId);
        List<BidFavorite> favorites = bidFavoriteDao.findByUserSeq(user.getUserSeq());
        if (favorites.isEmpty()) {
            return List.of();
        }

        List<Long> favoriteSeqs = favorites.stream()
                .map(BidFavorite::getFavoriteSeq)
                .toList();

        Map<Long, List<BidFavoriteAttachment>> attachmentMap = bidFavoriteAttachmentDao.findByFavoriteSeqs(favoriteSeqs)
                .stream()
                .collect(Collectors.groupingBy(BidFavoriteAttachment::getFavoriteSeq));

        return favorites.stream()
                .map(favorite -> BidFavoriteResponse.from(
                        favorite,
                        attachmentMap.getOrDefault(favorite.getFavoriteSeq(), Collections.emptyList())
                ))
                .toList();
    }

    @Override
    public BidFavoriteCheckResponse check(String userId, String bidNo, String bidOrd, LocalDate announceDate) {
        User user = requireUser(userId);
        boolean favorited = bidFavoriteDao.existsByUserAndBid(user.getUserSeq(), bidNo, bidOrd, announceDate);
        return new BidFavoriteCheckResponse(favorited);
    }

    @Override
    @Transactional
    public void add(String userId, BidFavoriteRequest request) {
        validateRequest(request);
        User user = requireUser(userId);

        if (bidFavoriteDao.existsByUserAndBid(
                user.getUserSeq(),
                request.getBidNo(),
                request.getBidOrd(),
                request.getAnnounceDate()
        )) {
            return;
        }

        BidDetailResponse detail = bidService.getDetail(
                request.getBidNo(),
                request.getBidOrd(),
                request.getAnnounceDate(),
                request.getIndustryCode(),
                request.getIndustryName()
        );

        BidFavorite favorite = BidFavorite.builder()
                .userSeq(user.getUserSeq())
                .bidNo(request.getBidNo())
                .bidOrd(request.getBidOrd())
                .announceDate(request.getAnnounceDate())
                .bidName(firstNonBlank(detail.getBidName(), request.getBidName()))
                .industry(firstNonBlank(detail.getIndustry(), request.getIndustry()))
                .contractMethod(firstNonBlank(detail.getContractMethod(), request.getContractMethod()))
                .openingDate(firstNonBlank(detail.getOpeningDate(), request.getOpeningDate()))
                .estimatedPrice(detail.getEstimatedPrice() != null ? detail.getEstimatedPrice() : request.getEstimatedPrice())
                .agency(firstNonBlank(detail.getAgency(), request.getAgency()))
                .detailUrl(firstNonBlank(detail.getDetailUrl(), request.getDetailUrl()))
                .detailContent(detail.getDetailContent())
                .contactName(detail.getContactName())
                .contactPhone(detail.getContactPhone())
                .contactEmail(detail.getContactEmail())
                .build();

        bidFavoriteDao.insert(favorite);

        try {
            createGoogleDriveFolder(favorite);
        } catch (RuntimeException ex) {
            log.warn("구글 드라이브 폴더 생성 실패: favoriteSeq={}", favorite.getFavoriteSeq(), ex);
        }

        try {
            bidFavoriteAttachmentService.saveAttachments(favorite.getFavoriteSeq(), detail.getAttachments());
        } catch (RuntimeException ex) {
            log.error("즐겨찾기 첨부파일 저장 중 오류: favoriteSeq={}", favorite.getFavoriteSeq(), ex);
        }
    }

    @Override
    @Transactional
    public void remove(String userId, String bidNo, String bidOrd, LocalDate announceDate) {
        User user = requireUser(userId);
        BidFavorite favorite = bidFavoriteDao.findOwnedByUserAndBid(
                user.getUserSeq(),
                bidNo,
                bidOrd,
                announceDate
        );
        if (favorite == null) {
            return;
        }

        try {
            String folderName = StringUtils.hasText(favorite.getBidName()) ? favorite.getBidName() : "미명";
            googleDriveService.deleteFavoriteFolder(
                    favorite.getGoogleDriveFolderId(),
                    googleDriveProperties.getSharedDriveId(),
                    folderName
            );
        } catch (RuntimeException ex) {
            log.warn("구글 드라이브 폴더 삭제 실패: favoriteSeq={}", favorite.getFavoriteSeq(), ex);
        }

        try {
            String projectName = StringUtils.hasText(favorite.getBidName()) ? favorite.getBidName() : "미명";
            notebookLMService.deleteProjectByName(projectName);
        } catch (RuntimeException ex) {
            log.warn("NotebookLM 프로젝트 삭제 실패: favoriteSeq={}", favorite.getFavoriteSeq(), ex);
        }

        bidFavoriteAttachmentService.deleteAttachments(favorite.getFavoriteSeq());
        bidFavoriteDao.deleteByUserAndBid(user.getUserSeq(), bidNo, bidOrd, announceDate);
    }

    private User requireUser(String userId) {
        User user = userDao.findByUserId(userId);
        if (user == null) {
            throw new BusinessException("존재하지 않는 사용자입니다.", HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    private void validateRequest(BidFavoriteRequest request) {
        if (!StringUtils.hasText(request.getBidNo())
                || !StringUtils.hasText(request.getBidOrd())
                || request.getAnnounceDate() == null
                || !StringUtils.hasText(request.getBidName())) {
            throw new BusinessException("즐겨찾기 저장에 필요한 공고 정보가 부족합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        return StringUtils.hasText(fallback) ? fallback.trim() : null;
    }

    private void createGoogleDriveFolder(BidFavorite favorite) {
        String folderName = StringUtils.hasText(favorite.getBidName()) ? favorite.getBidName() : "미명";
        GoogleDriveFileResponse folder = googleDriveService.findOrCreateFolder(
                googleDriveProperties.getSharedDriveId(),
                folderName
        );
        bidFavoriteDao.updateGoogleDriveFolderId(favorite.getFavoriteSeq(), folder.getId());
    }
}
