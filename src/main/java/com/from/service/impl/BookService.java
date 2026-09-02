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

// 널처리 유틸
import com.from.util.CmmUtil;

// Lombok 생성자 자동 생성
import lombok.RequiredArgsConstructor;

// Lombok 로그
import lombok.extern.slf4j.Slf4j;

// Spring Service Bean 등록
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;



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

        // Repository → Entity 조회
        Optional<BookEntity> entityOpt = bookRepository.findByTitleAndAuthor(title, author);

        // Service → DTO 변환
        if (entityOpt.isPresent()) {
            return Optional.of(toDTO(entityOpt.get()));
        }
        return Optional.empty();
    }


    // 알라딘 검색 결과를 books 테이블에 신규 저장 (book_id는 IDENTITY로 자동 채번)
    // findByTitleAndAuthor()에서 못 찾았을 때만 호출됨 (registerBook()의 orElseGet 분기)
    @Override
    public BookSearchDto save(String title, String author, String coverImage, String description, String category, String isbn13) {

        BookEntity entity = BookEntity.builder()
                .title(title)
                .author(author)
                .coverImage(coverImage)
                .description(description)
                .category(category)
                .isbn13(isbn13 == null || isbn13.isBlank() ? null : isbn13)
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

        Optional<BookEntity> entityOpt = bookRepository.findById(bookId);

        if (entityOpt.isPresent()) {
            return Optional.of(toDTO(entityOpt.get()));
        }
        return Optional.empty();
    }



  //사용자 책 목록 조회
    @Override
    public List<BookSearchDto> findByUserId(String userId) {

        // 사용자가 등록한 책 목록 조회
        List<BookEntity> entityList = bookRepository.findByUserId(userId);

        // Entity → DTO 변환
        List<BookSearchDto> result = new ArrayList<>();
        for (BookEntity entity : entityList) {
            result.add(toDTO(entity));
        }

        return result;
    }



    @Override
    //사용자와 책 기준으로 기존 독후감 존재 여부를 확인하고,
    // 있으면 수정(Update), 없으면 신규 등록(Insert)하는 Upsert 방식으로 구현했습니다.
    public void saveRating(String userId, Long bookId, int rating, String content) {
        bookRatingRepository.findByUserIdAndBookId(userId, bookId)
            .ifPresentOrElse(
                existing -> bookRatingRepository.save(BookRatingEntity.builder()
                    .id(existing.getId()).userId(userId).bookId(bookId)
                    .rating(rating).content(content)
                    .createdAt(existing.getCreatedAt())
                    .updatedAt(LocalDateTime.now())
                    .build()),
                () -> bookRatingRepository.save(BookRatingEntity.builder()
                    .userId(userId).bookId(bookId).rating(rating).content(content).build())
            );
    }

    // 특정 책(bookId)에 달린 모든 후기를 최신 수정순으로 조회
    // 비로그인 사용자도 볼 수 있으므로 currentUserId는 null일 수 있음 (그 경우 liked/isOwn은 항상 false)
    @Override
    public List<BookRatingDto> getRatings(Long bookId, String currentUserId) {

        // 1. 특정 책에 달린 후기를 최신 수정순으로 조회
        List<BookRatingEntity> ratingList = bookRatingRepository.findByBookIdOrderByUpdatedAtDesc(bookId);

        // 2. 화면에 전달할 DTO 리스트 생성
        List<BookRatingDto> result = new ArrayList<>();

        // 3. 후기 목록을 하나씩 반복하면서 DTO로 변환
        for (BookRatingEntity r : ratingList) {

            String uid = r.getUserId();

            // 4. 작성자 아이디 마스킹: 2자 이하면 전부 가림, 그 외엔 첫/끝 글자만 노출 (예: "abcdef" → "a***f")
            String maskedId = uid.length() <= 2 ? "***" : uid.charAt(0) + "***" + uid.charAt(uid.length() - 1);

            // 5. 작성자의 닉네임/프로필 이미지를 users 테이블에서 추가 조회 (탈퇴 등으로 없으면 마스킹된 ID로 대체)
            Optional<com.from.repository.entity.UserInfoEntity> userOpt = userInfoRepository.findByUserId(uid);

            String displayName = maskedId;
            String profileImageUrl = null;

            if (userOpt.isPresent()) {
                com.from.repository.entity.UserInfoEntity user = userOpt.get();
                if (user.getUsername() != null) {
                    displayName = user.getUsername();
                }
                profileImageUrl = user.getProfileImageUrl();
            }

            // 6. 후기 내용 null 방지
            String content = CmmUtil.nvl(r.getContent());

            // 7. 작성일 null 방지
            String createdAt = CmmUtil.nvl(
                    r.getCreatedAt() == null ? "" : r.getCreatedAt().toLocalDate().toString()
            );

            // 8. 좋아요 여부 / 본인 여부 (비로그인 시 currentUserId == null → 항상 false)
            boolean liked = currentUserId != null && bookReviewLikeRepository.existsByRatingIdAndUserId(r.getId(), currentUserId);
            boolean isOwn = currentUserId != null && r.getUserId().equals(currentUserId);

            // 9. Entity를 화면용 DTO로 변환
            result.add(new BookRatingDto(
                r.getId(),
                maskedId,
                displayName,
                profileImageUrl,
                r.getRating(),
                content,
                createdAt,
                bookReviewLikeRepository.countByRatingId(r.getId()),     // 좋아요 총 개수
                bookReviewCommentRepository.countByRatingId(r.getId()),  // 댓글 총 개수
                liked,
                isOwn
            ));
        }

        return result;
    }

    // 좋아요 토글: 이미 좋아요를 눌렀으면 삭제(취소), 안 눌렀으면 새로 저장
    // 처리 후 liked 여부 + 최신 좋아요 총 개수를 함께 반환 (프론트에서 하트 아이콘/카운트 즉시 갱신용)
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

    // 특정 후기(rating)에 달린 댓글을 등록순(오래된 순)으로 조회
    // 작성자 아이디는 마스킹("a***b" 형태)해서 DTO에 담아 반환
    @Override
    public List<BookCommentDto> getComments(Long ratingId) {

        // 1. 특정 후기에 달린 댓글을 등록순으로 조회
        List<BookReviewCommentEntity> commentList = bookReviewCommentRepository.findByRatingIdOrderByCreatedAtAsc(ratingId);

        // 2. 화면에 전달할 DTO 리스트 생성
        List<BookCommentDto> result = new ArrayList<>();

        // 3. 댓글 목록을 하나씩 반복하면서 DTO로 변환
        for (BookReviewCommentEntity c : commentList) {

            String uid = c.getUserId();

            // 4. 작성자 아이디 마스킹
            String masked = uid.length() <= 2 ? "***" : uid.charAt(0) + "***" + uid.charAt(uid.length() - 1);

            // 5. 작성일 null 방지
            String createdAt = CmmUtil.nvl(
                    c.getCreatedAt() == null ? "" : c.getCreatedAt().toLocalDate().toString()
            );

            result.add(new BookCommentDto(masked, c.getContent(), createdAt));
        }

        return result;
    }

    // 후기 삭제: ratingId + userId가 모두 일치할 때만 삭제 (다른 사람 후기 삭제 방지)
    // existsByIdAndUserId로 본인 소유 여부를 먼저 확인 → false면 권한 없음으로 처리
    @Override
    public boolean deleteRating(Long ratingId, String userId) {
        if (!bookRatingRepository.existsByIdAndUserId(ratingId, userId)) return false;
        bookRatingRepository.deleteById(ratingId);
        return true;
    }

    // 후기 수정: ratingId로 조회 후 userId가 작성자 본인인지 filter()로 검증
    // 본인이면 rating/content를 갱신하고 updatedAt을 현재 시각으로 변경(목록 정렬 시 맨 위로 오게 됨) → true
    // 본인이 아니거나 존재하지 않으면 → false (권한 없음)
    @Override
    public boolean updateMyRating(Long ratingId, String userId, int rating, String content) {

        // 1. 수정할 후기 조회
        Optional<BookRatingEntity> ratingOpt = bookRatingRepository.findById(ratingId);

        if (ratingOpt.isEmpty()) {
            return false; // 존재하지 않는 후기
        }

        BookRatingEntity r = ratingOpt.get();

        // 2. 작성자 본인인지 검증
        if (!r.getUserId().equals(userId)) {
            return false; // 권한 없음
        }

        // 3. rating/content 갱신, updatedAt을 현재 시각으로 변경 → 목록 맨 위로
        bookRatingRepository.save(BookRatingEntity.builder()
            .id(r.getId()).userId(r.getUserId()).bookId(r.getBookId())
            .rating(rating).content(content)
            .createdAt(r.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .build());

        return true;
    }



    // 마이페이지용: 내가 작성한 후기 전체를 최신순으로 조회
    // 후기(BookRatingEntity)에는 책 제목/저자가 없으므로, bookId로 BookEntity를 추가 조회해서 함께 담아줌
    // 책이 삭제된 경우 등 조회 실패 시 title/author는 빈 문자열로 처리
    @Override
    public List<MyRatingDto> getMyRatings(String userId) {

        log.info("{}.getMyRatings Start! - userId:{}", this.getClass().getName(), userId);

        // 1. 내가 작성한 후기를 최신순으로 조회
        // 여기서 DB에서 후기 Entity 목록을 가져옴.
        // findByUserIdOrderByCreatedAtDesc(userId)는 사용자별로 생성된 날짜를 desc로 나열
        List<BookRatingEntity> ratingList =
                bookRatingRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // 2. 화면에 전달할 DTO 리스트 생성
        //리스트(List)는 여러 개의 데이터를 순서대로 나열해 놓은 것
        //ArrayList 크기가 고정된 기존 배열의 단점을 보완하여,유동적으로 크기를 늘리고 줄일 수 있는 동적 배열

        //필드명 불일치(coverImage↔cover), 마스킹/카운트 같은 가공
        //  로직, 다른 테이블 추가 조회가 끼어 있어서 단순 변환이 안 먹힘
        List<MyRatingDto> result = new ArrayList<>();

        // 3. 후기 목록을 하나씩 반복하면서 DTO로 변환
        for (BookRatingEntity r : ratingList) {

            // 데이터 없으면 빈리스트 반환
            // 4. 후기에는 책 제목/저자가 없으므로 bookId로 책 정보 조회
            //후기에는 bookId만 있으니까, 실제 책 제목/저자를 가져오기 위해 책 테이블을 다시 조회.

            //옵셔널: 옵셔널(Optional)은 프로그래밍(주로 Swift나 Java 등)에서 '값이 있을 수도 있고,
            // 없을 수도 있는(Null)' 상태를 나타내는 데이터 타입이자 안정성 확보 기법입니다.
            // 값이 없는 상태를 안전하게 처리하여 프로그램이 예기치 않게 종료되는 것을 방지합니다

            Optional<BookEntity>  //타입
                    bookOpt // 뱐수명
                    = //대입
                    bookRepository.findById(r.getBookId());
                    //bookRepository.findById(r.getBookId())는 후기 테이블에 저장된 book_id를 이용해
                    // books 테이블에서 실제 책 정보를 조회하는 코드입니다.
                    // 후기에는 책 제목과 저자를 중복 저장하지 않고 정규화 관점에서 분리했습니다.

            // 5. 책 정보가 있으면 제목/저자 사용, 없으면 빈 문자열 처리(꺠짐 방지용도)
            String title = "";
            String author = "";
            String isbn13 = "";
            //책 정보가 있으면 제목/저자/isbn13 넣음. isbn13은 등록 당시 저장 안 됐으면 null일 수 있음
            if (bookOpt.isPresent()) {
                BookEntity book = bookOpt.get();
                title = CmmUtil.nvl(book.getTitle());
                author = CmmUtil.nvl(book.getAuthor());
                isbn13 = CmmUtil.nvl(book.getIsbn13());
            }

            // 6. content null 방지(널이면 빈 문자열로 처리 )
            String content = CmmUtil.nvl(r.getContent());

            // 7. 작성일 null 방지
            //작성일이 있으면 yyyy-MM-dd 형태 문자열로 변환.
            String createdAt = CmmUtil.nvl(
                    r.getCreatedAt() == null ? "" : r.getCreatedAt().toLocalDate().toString()
            );

            // 8. Entity를 화면용 DTO로 변환
            MyRatingDto dto = MyRatingDto.builder()
                    .id(r.getId())
                    .bookId(r.getBookId())
                    .bookTitle(title)
                    .bookAuthor(author)
                    .rating(r.getRating())
                    .content(content)
                    .createdAt(createdAt)
                    .isbn13(isbn13)
                    .build();

            // 9. 결과 리스트에 추가
            result.add(dto);
        }

        log.info("{}.getMyRatings End! - {}건", this.getClass().getName(), result.size());

        return result;
    }




    //빌더로 DTO반환
    private BookSearchDto toDTO(BookEntity entity) {
        // 빌더로 저장후에 DTO로 저장
        // BookSearchDto는 @Builder를 사용해
        // 빌더 패턴으로 DTO를 생성한 것입니다.

        return BookSearchDto.builder()

                .bookId(entity.getBookId())

                .title(entity.getTitle())

                .author(entity.getAuthor())

                .cover(entity.getCoverImage())

                .isbn(entity.getIsbn13())

                .description(entity.getDescription())

                .category(entity.getCategory())

                .createdAt(entity.getCreatedAt())

                .build();
    }
}