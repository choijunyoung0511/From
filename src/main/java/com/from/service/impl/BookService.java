package com.from.service.impl;

import com.from.dto.BookSearchDTO;
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
 * Repository는 Entity를 반환하지만, Service는 반드시 DTO로 변환하여 반환한다.
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
    public Optional<BookSearchDTO> findByTitleAndAuthor(String title, String author) {
        return bookRepository.findByTitleAndAuthor(title, author).map(this::toDTO);
    }

    /**
     * 새로운 책을 DB에 저장하고 DTO로 변환하여 반환한다.
     */
    @Override
    public BookSearchDTO save(String title, String author, String coverImage) {
        BookEntity entity = BookEntity.builder()
                .title(title)
                .author(author)
                .coverImage(coverImage)
                .build();
        return toDTO(bookRepository.save(entity));
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

        if (userBookRepository.existsById(id)) {
            return false;
        }

        userBookRepository.save(UserBookEntity.builder().id(id).build());

        readingLogRepository.save(ReadingLogEntity.builder()
                .userId(userId)
                .bookId(bookId)
                .readDate(LocalDate.now())
                .build());
        return true;
    }

    /**
     * 책 ID로 단건 조회한다.
     */
    @Override
    public Optional<BookSearchDTO> findById(Long bookId) {
        return bookRepository.findById(bookId).map(this::toDTO);
    }

    /**
     * 특정 유저가 등록한 책 목록을 최신 등록순으로 조회한다.
     */
    @Override
    public List<BookSearchDTO> findByUserId(String userId) {
        return bookRepository.findByUserId(userId).stream()
                .map(this::toDTO)
                .toList();
    }

    /** Entity → DTO 변환 헬퍼 */
    private BookSearchDTO toDTO(BookEntity entity) {
        return BookSearchDTO.builder()
                .bookId(entity.getBookId())
                .title(entity.getTitle())
                .author(entity.getAuthor())
                .cover(entity.getCoverImage())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}