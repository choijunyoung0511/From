package com.from.service;

import com.from.dto.BoardDto;

import java.util.List;
import java.util.Optional;

public interface IBoardService {

    //게시글 목록 조회 (최신순)
    List<BoardDto> findAll();

    //게시글 상세 조회. 없으면 Optional.empty()
    Optional<BoardDto> findById(Long id);

    //게시글 저장 후 생성된 게시글 id 반환
    Long save(String userId, String title, String content);

    //게시글 수정. 게시글이 없거나 작성자 본인이 아니면 false
    boolean update(Long id, String userId, String title, String content);

    //게시글 삭제. 게시글이 없거나 작성자 본인이 아니면 false
    boolean delete(Long id, String userId);
}
