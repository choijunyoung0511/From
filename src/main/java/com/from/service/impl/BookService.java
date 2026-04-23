package com.from.service.impl;

import com.from.repository.BookRepository;
import com.from.repository.ReadingLogRepository;
import com.from.repository.UserBookRepository;
import com.from.repository.entity.BookEntity;
import com.from.repository.entity.ReadingLogEntity;
import com.from.repository.entity.UserBookEntity;
import com.from.repository.entity.UserBookId;
import com.from.service.IBookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 책 관련 비즈니스 로직 서비스 구현체.
 * 책 저장, 유저-책 연결, 독서 기록 등록을 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookService implements IBookService {

    private final BookRepository bookRepository;
    private final UserBookRepository userBookRepository;
    private final ReadingLogRepository readingLogRepository;

    /**
     * 제목과 저자로 이미 DB에 저장된 책을 조회한다.
     * 같은 책이 중복 저장되지 않도록 BookController에서 등록 전에 먼저 호출한다.
     */
    @Override
    public Optional<BookEntity> findByTitleAndAuthor(String title, String author) {
        return bookRepository.findByTitleAndAuthor(title, author);
    }

    /**
     * 새로운 책을 DB에 저장하고 저장된 엔티티를 반환한다.
     */
    @Override
    public BookEntity save(BookEntity book) {
        return bookRepository.save(book);
    }

    /**
     * 유저-책 연결(user_books)을 저장하고 독서 로그(reading_logs)를 기록한다.
     * 이미 등록된 책(같은 userId + bookId 조합)이면 저장하지 않고 false를 반환한다.
     *
     * @return true = 등록 성공, false = 이미 등록된 책
     */
    @Override
    public boolean saveUserBook(String userId, Long bookId) {
        UserBookId id = new UserBookId(userId, bookId);

        // 이미 등록된 책인지 복합키로 중복 확인
        if (userBookRepository.existsById(id)) {
            return false;
        }

        // user_books에 유저-책 연결 저장
        userBookRepository.save(UserBookEntity.builder().id(id).build());

        // reading_logs에 오늘 날짜로 독서 기록 저장 (랭킹·대시보드에 활용)
        readingLogRepository.save(ReadingLogEntity.builder()
                .userId(userId)
                .bookId(bookId)
                .readDate(LocalDate.now())
                .build());
        return true;
    }

    /**
     * 책 ID로 단건 조회한다.
     * ImageController에서 이미지 결과 상세 조회 시 책 제목을 가져올 때 사용한다.
     */
    @Override
    public Optional<BookEntity> findById(Long bookId) {
        return bookRepository.findById(bookId);
    }

    /**
     * 특정 유저가 등록한 책 목록을 최신 등록순으로 조회한다.
     * 마이페이지, 대시보드, 독후감 생성, 이미지 생성의 책 선택 드롭다운에 사용된다.
     */
    @Override
    public List<BookEntity> findByUserId(String userId) {
        return bookRepository.findByUserId(userId);
    }
}