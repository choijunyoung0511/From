package com.from.service.impl;

import com.from.dto.BoardDto;
import com.from.repository.BoardRepository;
import com.from.repository.UserInfoRepository;
import com.from.repository.entity.BoardEntity;
import com.from.service.IBoardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class BoardService implements IBoardService {

    private final BoardRepository boardRepository;
    //작성자 닉네임 조회를 위해 UserInfoRepository를 주입 받음 (FollowService와 동일한 패턴)
    private final UserInfoRepository userInfoRepository;




    @Override
    //작성된 글 목록 정렬
    //레포지토리에서 작성일 내림차순으로 엔터티 목록 조회 -> 엔터티를 toDto()로 변환-> dto 목록을 컨트롤러에 반환 model에 담아 화면에 표시
    public List<BoardDto> findAll() {
        log.info("{}.findAll Start!", this.getClass().getName());
        //DB에서 작성일 내림차순으로 조회
        List<BoardDto> result = boardRepository.findAllByOrderByCreatedAtDesc()
                //조회한 글들을 처리할 흐름 생성
                .stream()
                //각 Entity를 DTO로 반환
                .map(this::toDto)
                //dto를 리스트에 담음
                .toList();
        log.info("{}.findAll End!", this.getClass().getName());
        return result;
    }


    //게시글 엔터티 하나를 화면 표시용 DTO로 변환. 작성자 닉네임은 없으면 아이디로 대체(Optional 처리)
    //엔터티를 DTO로 변환하는 코드임
    private BoardDto toDto(BoardEntity entity) {
        String writer = userInfoRepository.findByUserId(entity.getUserId())
                .map(user -> Optional.ofNullable(user.getUsername()).orElse(entity.getUserId()))
                .orElse(entity.getUserId());

        return new BoardDto(
                entity.getId(),
                entity.getUserId(),
                writer,
                entity.getTitle(),
                entity.getContent(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }



    @Override
    public Optional<BoardDto> findById(Long id) {
        log.info("{}.findById Start!", this.getClass().getName());
        Optional<BoardDto> result = boardRepository.findById(id).map(this::toDto);
        log.info("{}.findById End!", this.getClass().getName());
        return result;
    }


    //글 쓴거 저장
    @Override
    public Long save(String userId, String title, String content) {
        log.info("{}.save Start!", this.getClass().getName());
        //엔터티를 만듬
        BoardEntity saved = boardRepository.save(
                BoardEntity.builder()
                        .userId(userId)
                        .title(title)
                        .content(content)
                        .build()
        );
        log.info("{}.save End!", this.getClass().getName());
        return saved.getId();
    }

    @Override
    @Transactional //메서드 안의 DB작업을 하나의 작업 단위로 묶어 처리하도록 스프링에 알려주는 어노테이션
                   //JPA가 조회한 Entity의 처음 상태를 기억하고 있다가 바뀐부분을 찾아 DB에 반영 (변경 감지)
    public boolean update(Long id, String userId, String title, String content) {
        log.info("{}.update Start!", this.getClass().getName());

        Optional<BoardEntity> existing = boardRepository.findById(id);
        if (existing.isEmpty() || !existing.get().getUserId().equals(userId)) {
            return false; //게시글이 없거나 작성자 본인이 아님
        }

        BoardEntity board = existing.get();
        board.setTitle(title);
        board.setContent(content);

        log.info("{}.update End!", this.getClass().getName());
        return true;
    }

    @Override
    public boolean delete(Long id, String userId) {
        log.info("{}.delete Start!", this.getClass().getName());

        Optional<BoardEntity> existing = boardRepository.findById(id);
        if (existing.isEmpty() || !existing.get().getUserId().equals(userId)) {
            return false; //게시글이 없거나 작성자 본인이 아님
        }

        boardRepository.delete(existing.get());

        log.info("{}.delete End!", this.getClass().getName());
        return true;
    }
}
