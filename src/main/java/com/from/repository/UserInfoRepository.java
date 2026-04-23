package com.from.repository;

import com.from.repository.entity.UserInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * users 테이블에 접근하는 JPA Repository.
 * JpaRepository를 상속하면 기본 CRUD(save, findById, delete 등)가 자동으로 제공된다.
 *
 * 메서드 이름 규칙(Query Method): Spring Data JPA가 메서드 이름을 분석하여
 * SELECT 쿼리를 자동 생성한다. 별도의 SQL 작성이 필요 없다.
 */
@Repository
public interface UserInfoRepository extends JpaRepository<UserInfoEntity, String> {

    /**
     * 로그인 아이디로 사용자를 조회한다.
     * 로그인, 아이디 중복 체크, 비밀번호 변경 등에서 사용한다.
     *
     * @param userId 로그인 아이디
     * @return 해당 아이디의 사용자 (없으면 Optional.empty())
     */
    Optional<UserInfoEntity> findByUserId(String userId);

    /**
     * 암호화된 이메일로 사용자를 조회한다.
     * 이메일 중복 체크 시 사용한다. (DB에 AES 암호화된 값으로 저장되어 있으므로 암호화 후 비교)
     *
     * @param email AES 암호화된 이메일
     * @return 해당 이메일의 사용자 (없으면 Optional.empty())
     */
    Optional<UserInfoEntity> findByEmail(String email);

    /**
     * 실명과 암호화된 이메일로 사용자를 조회한다.
     * 아이디 찾기, 비밀번호 찾기에서 사용자 본인 확인에 사용한다.
     *
     * @param name  실명
     * @param email AES 암호화된 이메일
     * @return 조건에 맞는 사용자 (없으면 Optional.empty())
     */
    Optional<UserInfoEntity> findByNameAndEmail(String name, String email);
}