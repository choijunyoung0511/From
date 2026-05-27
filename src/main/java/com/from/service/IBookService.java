package com.from.service;

import com.from.dto.BookCommentDto;
import com.from.dto.BookRatingDto;
import com.from.dto.BookSearchDto;
import com.from.dto.LikeToggleDto;
import com.from.dto.MyRatingDto;

import java.util.List;
import java.util.Optional;

public interface IBookService {

    // 제목 + 저자로 책 조회 (중복 등록 방지)
    Optional<BookSearchDto> findByTitleAndAuthor(String title, String author);

    // 책 저장
    BookSearchDto save(String title, String author, String coverImage, String description, String category);

    // 유저-책 연결 (이미 연결된 경우 false 반환)
    boolean saveUserBook(String userId, Long bookId);

    // ID로 책 단건 조회
    Optional<BookSearchDto> findById(Long bookId);

    // 유저가 등록한 책 목록 조회 (최신순)
    List<BookSearchDto> findByUserId(String userId);

    // 책 후기 저장 (이미 작성했으면 수정)
    void saveRating(String userId, Long bookId, int rating, String content);

    // 책 후기 목록 조회 (ratingId·likeCount·commentCount·isLiked 포함)
    List<BookRatingDto> getRatings(Long bookId, String currentUserId);

    // 후기 좋아요 토글 — 좋아요 눌렀으면 취소, 안 눌렀으면 추가
    LikeToggleDto toggleLike(Long ratingId, String userId);

    // 댓글 추가
    void addComment(Long ratingId, String userId, String content);

    // 댓글 목록 조회
    List<BookCommentDto> getComments(Long ratingId);

    // 내 후기 삭제 (본인만 가능)
    boolean deleteRating(Long ratingId, String userId);

    // 내 후기 수정 (본인만 가능)
    boolean updateMyRating(Long ratingId, String userId, int rating, String content);

    // 내가 작성한 후기 목록 조회 (마이페이지용)
    List<MyRatingDto> getMyRatings(String userId);
}