package com.from.repository;

import com.from.repository.entity.UserInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


//사용자 테이블에 접근 users
@Repository
public interface UserInfoRepository extends JpaRepository<UserInfoEntity, String> {


    //로그인 으로 사용자 조회
    Optional<UserInfoEntity> findByUserId(String userId);

    //암호화된 이메일
    Optional<UserInfoEntity> findByEmail(String email);


    //실명과 암호화된 이메일로 사용자 조회
    Optional<UserInfoEntity> findByNameAndEmail(String name, String email);

    @Modifying
    @Transactional
    @Query("UPDATE UserInfoEntity u SET u.profileImageUrl = :url WHERE u.userId = :userId")
    void updateProfileImageUrl(@Param("userId") String userId, @Param("url") String url);
}