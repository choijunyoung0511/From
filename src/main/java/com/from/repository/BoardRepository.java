package com.from.repository;

import com.from.repository.entity.BoardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

//DB와 연결해서 게시글 데이터를 저장, 조회, 삭제하는 레포지토리
public interface BoardRepository extends JpaRepository<BoardEntity, Long> {

    //최신 게시글이 먼저 보이도록 작성일 역순 정렬
    List<BoardEntity> findAllByOrderByCreatedAtDesc();
}
