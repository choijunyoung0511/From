package com.from.service.impl;

// 책 검색 결과 DTO
import com.from.dto.BookCommentDto;
import com.from.dto.BookRatingDto;
import com.from.dto.BookSearchDto;
import com.from.dto.LikeToggleDto;
import com.from.dto.MyRatingDto;

// Repository 계층 (DB 접근)
import com.from.repository.BookRatingRepository;
import com.from.repository.BookRepository;
import com.from.repository.BookReviewCommentRepository;
import com.from.repository.BookReviewLikeRepository;
import com.from.repository.ReadingLogRepository;
import com.from.repository.UserBookRepository;
import com.from.repository.UserInfoRepository;

// Entity (DB 테이블 매핑 객체)
import com.from.repository.entity.BookEntity;
import com.from.repository.entity.BookRatingEntity;
import com.from.repository.entity.BookReviewCommentEntity;
import com.from.repository.entity.BookReviewLikeEntity;
import com.from.repository.entity.ReadingLogEntity;
import com.from.repository.entity.UserBookEntity;
import com.from.repository.entity.UserBookId;

// Service 인터페이스
import com.from.service.IBookService;

// Lombok 생성자 자동 생성
import lombok.RequiredArgsConstructor;

// Lombok 로그
import lombok.extern.slf4j.Slf4j;

// Spring Service Bean 등록
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;



        //책 저장,책 중복 검사,유저-책연결,독서 기록,책 조회

@Slf4j // 로그 출력용 객체 생성
@Service // Spring Bean 등록 (Controller에서 주입 가능)
@RequiredArgsConstructor // final 필드 생성자 자동 생성
public class BookService implements IBookService {


    private final BookRepository bookRepository;


    private final UserBookRepository userBookRepository;


    private final ReadingLogRepository readingLogRepository;
    private final BookRatingRepository bookRatingRepository;
    private final BookReviewLikeRepository bookReviewLikeRepository;
    private final BookReviewCommentRepository bookReviewCommentRepository;
    private final UserInfoRepository userInfoRepository;



    @Override
    public Optional<BookSearchDto> findByTitleAndAuthor(String title, String author) {

        // Repository → Entity 반환
        // Service → DTO 변환
        return bookRepository
                .findByTitleAndAuthor(title, author)
                .map(this::toDTO);
    }



    @Override
    public BookSearchDto save(String title, String author, String coverImage, String description, String category) {

        BookEntity entity = BookEntity.builder()
                .title(title)
                .author(author)
                .coverImage(coverImage)
                .description(description)
                .category(category)
                .build();

        return toDTO(bookRepository.save(entity));
    }



    @Override
    public boolean saveUserBook(String userId, Long bookId) {

        UserBookId id = new UserBookId(userId, bookId);


      //중복 저장 방지,책 등록 중복저장 방지
        if (userBookRepository.existsById(id)) {
            return false;
        }

        //복합키 user.id + book.id
        userBookRepository.save(
                UserBookEntity.builder()
                        .id(id)
                        .build()
        );


       //독서 잔디,빌더로 저장
        readingLogRepository.save(
                ReadingLogEntity.builder()
                        .userId(userId)
                        .bookId(bookId)
                        .readDate(LocalDate.now()) // 오늘 날짜
                        .build()
        );

        return true;
    }



    //아이디로 조회
    @Override
    public Optional<BookSearchDto> findById(Long bookId) {

        return bookRepository
                .findById(bookId)
                .map(this::toDTO);
    }



  //사용자 책 목록 조회
    @Override
    public List<BookSearchDto> findByUserId(String userId) {

        return bookRepository
                .findByUserId(userId)
                .stream()

                // Entity → DTO 변환
                .map(this::toDTO)

                // List 생성
                .toList();
    }



    @Override
    public void saveRating(String userId, Long bookId, int rating, String content) {
        //후기 작성
        if (bookRatingRepository.findByUserIdAndBookId(userId, bookId).isPresent()) {
            // 이미 작성한 후기 → JPQL UPDATE (Setter 없이 필드만 변경)
            bookRatingRepository.updateRating(userId, bookId, rating, content);
        } else {
            // 최초 작성 → INSERT
            bookRatingRepository.save(BookRatingEntity.builder()
                .userId(userId).bookId(bookId).rating(rating).content(content).build());
        }
    }

    @Override
    public List<BookRatingDto> getRatings(Long bookId, String currentUserId) {
        return bookRatingRepository.findByBookIdOrderByUpdatedAtDesc(bookId).stream()
            .map(r -> {
                String uid = r.getUserId();
                String maskedId = uid.length() <= 2 ? "***" : uid.charAt(0) + "***" + uid.charAt(uid.length() - 1);
                var userOpt = userInfoRepository.findByUserId(uid);
                String displayName = userOpt.map(u -> u.getUsername() != null ? u.getUsername() : maskedId).orElse(maskedId);
                String profileImageUrl = userOpt.map(com.from.repository.entity.UserInfoEntity::getProfileImageUrl).orElse(null);
                return new BookRatingDto(
                    r.getId(),
                    maskedId,
                    displayName,
                    profileImageUrl,
                    r.getRating(),
                    r.getContent() == null ? "" : r.getContent(),
                    r.getCreatedAt() != null ? r.getCreatedAt().toLocalDate().toString() : "",
                    bookReviewLikeRepository.countByRatingId(r.getId()),
                    bookReviewCommentRepository.countByRatingId(r.getId()),
                    currentUserId != null && bookReviewLikeRepository.existsByRatingIdAndUserId(r.getId(), currentUserId),
                    currentUserId != null && r.getUserId().equals(currentUserId)
                );
            })
            .collect(Collectors.toList());
    }

    @Override
    public LikeToggleDto toggleLike(Long ratingId, String userId) {
        var existing = bookReviewLikeRepository.findByRatingIdAndUserId(ratingId, userId);
        boolean liked;
        if (existing.isPresent()) {
            bookReviewLikeRepository.delete(existing.get());
            liked = false;
        } else {
            bookReviewLikeRepository.save(BookReviewLikeEntity.builder().ratingId(ratingId).userId(userId).build());
            liked = true;
        }
        return new LikeToggleDto(liked, bookReviewLikeRepository.countByRatingId(ratingId));
    }

    @Override
    public void addComment(Long ratingId, String userId, String content) {
        //댓글
        bookReviewCommentRepository.save(BookReviewCommentEntity.builder()
            .ratingId(ratingId).userId(userId).content(content).build());
    }

    @Override
    public List<BookCommentDto> getComments(Long ratingId) {
        return bookReviewCommentRepository.findByRatingIdOrderByCreatedAtAsc(ratingId).stream()
            .map(c -> {
                String uid = c.getUserId();
                String masked = uid.length() <= 2 ? "***" : uid.charAt(0) + "***" + uid.charAt(uid.length() - 1);
                return new BookCommentDto(
                    masked,
                    c.getContent(),
                    c.getCreatedAt() != null ? c.getCreatedAt().toLocalDate().toString() : ""
                );
            })
            .collect(Collectors.toList());
    }

    @Override
    public boolean deleteRating(Long ratingId, String userId) {
        if (!bookRatingRepository.existsByIdAndUserId(ratingId, userId)) return false;
        bookRatingRepository.deleteById(ratingId);
        return true;
    }

    @Override
    public boolean updateMyRating(Long ratingId, String userId, int rating, String content) {
        return bookRatingRepository.findById(ratingId)
            .filter(r -> r.getUserId().equals(userId))
            .map(r -> {
                bookRatingRepository.save(BookRatingEntity.builder()
                    .id(r.getId()).userId(r.getUserId()).bookId(r.getBookId())
                    .rating(rating).content(content)
                    .createdAt(r.getCreatedAt())
                    .updatedAt(java.time.LocalDateTime.now()) // 수정 시 갱신 → 목록 맨 위로
                    .build());
                return true;
            }).orElse(false);
    }

    @Override
    public List<MyRatingDto> getMyRatings(String userId) {
        return bookRatingRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(r -> {
                Optional<BookEntity> bookOpt = bookRepository.findById(r.getBookId());
                return new MyRatingDto(
                    r.getId(),
                    r.getBookId(),
                    bookOpt.map(BookEntity::getTitle).orElse(""),
                    bookOpt.map(BookEntity::getAuthor).orElse(""),
                    r.getRating(),
                    r.getContent() == null ? "" : r.getContent(),
                    r.getCreatedAt() != null ? r.getCreatedAt().toLocalDate().toString() : ""
                );
            }).collect(Collectors.toList());
    }

    //빌더로 DTO반환
    private BookSearchDto toDTO(BookEntity entity) {
        // 빌더로 저장후에 DTO로 저장

        return BookSearchDto.builder()

                .bookId(entity.getBookId())

                .title(entity.getTitle())

                .author(entity.getAuthor())

                .cover(entity.getCoverImage())

                .description(entity.getDescription())

                .category(entity.getCategory())

                .createdAt(entity.getCreatedAt())

                .build();
    }
}