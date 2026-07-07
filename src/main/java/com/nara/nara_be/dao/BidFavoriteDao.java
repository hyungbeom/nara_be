package com.nara.nara_be.dao;

import com.nara.nara_be.domain.BidFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface BidFavoriteDao {

    List<BidFavorite> findByUserSeq(@Param("userSeq") Long userSeq);

    boolean existsByUserAndBid(
            @Param("userSeq") Long userSeq,
            @Param("bidNo") String bidNo,
            @Param("bidOrd") String bidOrd,
            @Param("announceDate") LocalDate announceDate
    );

    void insert(BidFavorite favorite);

    void deleteByUserAndBid(
            @Param("userSeq") Long userSeq,
            @Param("bidNo") String bidNo,
            @Param("bidOrd") String bidOrd,
            @Param("announceDate") LocalDate announceDate
    );

    Long findFavoriteSeqByUserAndBid(
            @Param("userSeq") Long userSeq,
            @Param("bidNo") String bidNo,
            @Param("bidOrd") String bidOrd,
            @Param("announceDate") LocalDate announceDate
    );

    BidFavorite findOwnedByFavoriteSeq(
            @Param("favoriteSeq") Long favoriteSeq,
            @Param("userSeq") Long userSeq
    );

    BidFavorite findOwnedByUserAndBid(
            @Param("userSeq") Long userSeq,
            @Param("bidNo") String bidNo,
            @Param("bidOrd") String bidOrd,
            @Param("announceDate") LocalDate announceDate
    );

    void updateGoogleDriveFolderId(
            @Param("favoriteSeq") Long favoriteSeq,
            @Param("googleDriveFolderId") String googleDriveFolderId
    );
}
