package com.from.service.impl;

// 책 검색 결과 DTO
import com.from.dto.BookSearchDTO;

// Repository 계층 (DB 접근)
import com.from.repository.BookRepository;
import com.from.repository.ReadingLogRepository;
import com.from.repository.UserBookRepository;

// Entity (DB 테이블 매핑 객체)
import com.from.repository.entity.BookEntity;
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



        //책 저장,책 중복 검사,유저-책연결,독서 기록,책 조회

@Slf4j // 로그 출력용 객체 생성
@Service // Spring Bean 등록 (Controller에서 주입 가능)
@RequiredArgsConstructor // final 필드 생성자 자동 생성
public class BookService implements IBookService {


    /**
     * BookRepository
     *
     * books 테이블 접근 Repository
     *
     * 역할
     * 책 정보 저장 / 조회
     */
    private final BookRepository bookRepository;


    /**
     * UserBookRepository
     *
     * user_books 테이블 접근 Repository
     *
     * 역할
     * 사용자와 책 관계 저장
     *
     * 예
     *
     * userId + bookId
     */
    private final UserBookRepository userBookRepository;


    /**
     * ReadingLogRepository
     *
     * reading_logs 테이블 접근 Repository
     *
     * 역할
     * 독서 활동 기록 저장
     *
     * 잔디 차트 생성 데이터
     */
    private final ReadingLogRepository readingLogRepository;



    /**
     * 책 중복 검사 메서드
     *
     * 목적
     * 같은 책이 DB에 이미 있는지 확인
     *
     * 호출 위치
     *
     * BookController
     *     ↓
     * findByTitleAndAuthor()
     *
     * 예
     *
     * "어린왕자 + 생텍쥐페리"
     *
     * 이미 DB에 존재하면
     * 새로운 책을 생성하지 않는다.
     */
    @Override
    public Optional<BookSearchDTO> findByTitleAndAuthor(String title, String author) {

        // Repository → Entity 반환
        // Service → DTO 변환
        return bookRepository
                .findByTitleAndAuthor(title, author)
                .map(this::toDTO);
    }



    /**
     * 새로운 책 저장
     *
     * 흐름
     *
     * Controller
     *     ↓
     * Service (현재 메서드)
     *     ↓
     * Repository.save()
     *     ↓
     * DB 저장
     *
     * 이후 DTO로 변환하여 반환
     */
    @Override
    public BookSearchDTO save(String title, String author, String coverImage, String description, String category) {

        BookEntity entity = BookEntity.builder()
                .title(title)
                .author(author)
                .coverImage(coverImage)
                .description(description)
                .category(category)
                .build();

        return toDTO(bookRepository.save(entity));
    }



    /**
     * 유저-책 연결 저장
     *
     * 저장되는 테이블
     *
     * user_books
     *
     * 구조
     *
     * userId + bookId
     *
     * 그리고 동시에
     *
     * reading_logs 기록도 저장한다
     *
     * 이유
     *
     * 독서 기록 데이터를 기반으로
     * 잔디 차트를 생성하기 위해
     */
    @Override
    public boolean saveUserBook(String userId, Long bookId) {

        UserBookId id = new UserBookId(userId, bookId);


      //중복 저장 방지
        if (userBookRepository.existsById(id)) {
            return false;
        }

        //복합키 user.id + book.id
        userBookRepository.save(
                UserBookEntity.builder()
                        .id(id)
                        .build()
        );


       //독서 잔디
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
    public Optional<BookSearchDTO> findById(Long bookId) {

        return bookRepository
                .findById(bookId)
                .map(this::toDTO);
    }



  //사용자 책 목록 조회
    @Override
    public List<BookSearchDTO> findByUserId(String userId) {

        return bookRepository
                .findByUserId(userId)
                .stream()

                // Entity → DTO 변환
                .map(this::toDTO)

                // List 생성
                .toList();
    }



  //빌더로 DTO반환
    private BookSearchDTO toDTO(BookEntity entity) {

        return BookSearchDTO.builder()

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