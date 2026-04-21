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

@Slf4j
@Service
@RequiredArgsConstructor
public class BookService implements IBookService {

    private final BookRepository bookRepository;
    private final UserBookRepository userBookRepository;
    private final ReadingLogRepository readingLogRepository;

    @Override
    public Optional<BookEntity> findByTitleAndAuthor(String title, String author) {
        return bookRepository.findByTitleAndAuthor(title, author);
    }

    @Override
    public BookEntity save(BookEntity book) {
        return bookRepository.save(book);
    }

    // 유저-책 연결 + reading_logs 기록 (이미 연결된 경우 false 반환)
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

    @Override
    public Optional<BookEntity> findById(Long bookId) {
        return bookRepository.findById(bookId);
    }

    @Override
    public List<BookEntity> findByUserId(String userId) {
        return bookRepository.findByUserId(userId);
    }
}