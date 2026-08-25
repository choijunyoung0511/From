package com.from.repository;

import com.from.repository.entity.FollowEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

//DB와 연결해서 팔로우 데이터를 저장,조회,삭제하는 레포지토리
//중복 팔로우 확인, 팔로우 취소, 팔로워/팔로잉 개수 조회를 위해 만든 Repository. 메서드 이름 기반으로 JPA가 쿼리를 자동 생성한다.



//상속 받아서 DB기능 사용 가능 (관리할 엔터티 + PK타입)
public interface FollowRepository extends JpaRepository<FollowEntity, Long> {
    boolean existsByFollowerIdAndFollowingId(String followerId, String followingId); //이미 팔로우 중인지 확인


    //두 사용자 사이의 실제 팔로우 데이터를 DB에서 찾아가서 가져오는 메서드임,A->B관계를 조회 없을수도 있기 때문에 Optional
    Optional<FollowEntity> findByFollowerIdAndFollowingId(String followerId, String followingId);


    long countByFollowingId(String followingId); //해당 사용자를 팔로우하는 사람 수 (팔로워 수)
    long countByFollowerId(String followerId);   //해당 사용자가 팔로우하는 사람 수 (팔로잉 수)
}
