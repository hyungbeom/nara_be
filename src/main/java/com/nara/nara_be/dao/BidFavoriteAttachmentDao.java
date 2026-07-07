package com.nara.nara_be.dao;

import com.nara.nara_be.domain.BidFavoriteAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BidFavoriteAttachmentDao {

    List<BidFavoriteAttachment> findByFavoriteSeq(@Param("favoriteSeq") Long favoriteSeq);

    List<BidFavoriteAttachment> findByFavoriteSeqs(@Param("favoriteSeqs") List<Long> favoriteSeqs);

    BidFavoriteAttachment findByAttachmentSeq(@Param("attachmentSeq") Long attachmentSeq);

    BidFavoriteAttachment findOwnedAttachment(
            @Param("attachmentSeq") Long attachmentSeq,
            @Param("userSeq") Long userSeq
    );

    BidFavoriteAttachment findConvertedPdf(
            @Param("favoriteSeq") Long favoriteSeq,
            @Param("originalFileName") String originalFileName
    );

    void insert(BidFavoriteAttachment attachment);

    void deleteByFavoriteSeq(@Param("favoriteSeq") Long favoriteSeq);

    void updateGoogleDriveFileId(
            @Param("attachmentSeq") Long attachmentSeq,
            @Param("googleDriveFileId") String googleDriveFileId
    );
}
