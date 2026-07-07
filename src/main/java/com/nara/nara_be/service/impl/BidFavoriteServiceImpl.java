package com.nara.nara_be.service.impl;

import com.nara.nara_be.dao.BidFavoriteDao;
import com.nara.nara_be.dao.UserDao;
import com.nara.nara_be.domain.BidFavorite;
import com.nara.nara_be.domain.User;
import com.nara.nara_be.dto.BidFavoriteCheckResponse;
import com.nara.nara_be.dto.BidFavoriteRequest;
import com.nara.nara_be.dto.BidFavoriteResponse;
import com.nara.nara_be.exception.BusinessException;
import com.nara.nara_be.service.BidFavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidFavoriteServiceImpl implements BidFavoriteService {

    private final BidFavoriteDao bidFavoriteDao;
    private final UserDao userDao;

    @Override
    public List<BidFavoriteResponse> findAll(String userId) {
        User user = requireUser(userId);
        return bidFavoriteDao.findByUserSeq(user.getUserSeq()).stream()
                .map(BidFavoriteResponse::from)
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

        BidFavorite favorite = BidFavorite.builder()
                .userSeq(user.getUserSeq())
                .bidNo(request.getBidNo())
                .bidOrd(request.getBidOrd())
                .announceDate(request.getAnnounceDate())
                .bidName(request.getBidName())
                .industry(request.getIndustry())
                .contractMethod(request.getContractMethod())
                .openingDate(request.getOpeningDate())
                .estimatedPrice(request.getEstimatedPrice())
                .agency(request.getAgency())
                .detailUrl(request.getDetailUrl())
                .build();

        bidFavoriteDao.insert(favorite);
    }

    @Override
    @Transactional
    public void remove(String userId, String bidNo, String bidOrd, LocalDate announceDate) {
        User user = requireUser(userId);
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
}
