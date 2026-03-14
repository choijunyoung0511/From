package com.from.mapper;

import com.from.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    String checkUsernameExists(String username);
    String checkEmailExists(String email);
    int insertUser(User user);
    int insertKakaoUser(User user);
    User findByUsername(String username);
    User findByUserId(Long userId);
    User findByKakaoId(Long kakaoId);
    int deleteUser(Long userId);
    int updatePassword(User user);  // 비밀번호 찾기용 (기존)

    // 마이페이지 비밀번호 변경용 (추가)
    int updatePasswordById(@Param("userId") Long userId, @Param("password") String password);

    User findByNameAndEmail(User user);
}