package com.from.service.impl;

import com.from.config.EncryptUtil;
import com.from.dto.UserInfoDto;
import com.from.repository.UserInfoRepository;
import com.from.repository.entity.UserInfoEntity;
import com.from.service.IEmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserInfoServiceTest {

    @Mock
    private UserInfoRepository userInfoRepository;

    @Mock
    private IEmailService emailService;

    @InjectMocks
    private UserInfoService userInfoService;

    @Test
    void 로그인_아이디와_비밀번호가_일치하면_사용자정보를_반환한다() throws Exception {
        String rawPassword = "password123";
        UserInfoEntity entity = UserInfoEntity.builder()
                .userId("testuser")
                .username("tester")
                .name("테스트")
                .password(EncryptUtil.encryptSHA256(rawPassword))
                .email(EncryptUtil.encryptAES("test@example.com"))
                .build();
        when(userInfoRepository.findByUserId("testuser")).thenReturn(Optional.of(entity));

        UserInfoDto result = userInfoService.login("testuser", rawPassword);

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo("testuser");
        assertThat(result.email()).isEqualTo("test@example.com"); // AES 복호화되어 반환
    }

    @Test
    void 로그인_비밀번호가_틀리면_null을_반환한다() throws Exception {
        UserInfoEntity entity = UserInfoEntity.builder()
                .userId("testuser")
                .password(EncryptUtil.encryptSHA256("correctPassword"))
                .email(EncryptUtil.encryptAES("test@example.com"))
                .build();
        when(userInfoRepository.findByUserId("testuser")).thenReturn(Optional.of(entity));

        UserInfoDto result = userInfoService.login("testuser", "wrongPassword");

        assertThat(result).isNull();
    }

    @Test
    void 로그인_존재하지_않는_아이디면_null을_반환한다() throws Exception {
        when(userInfoRepository.findByUserId("ghost")).thenReturn(Optional.empty());

        UserInfoDto result = userInfoService.login("ghost", "anyPassword");

        assertThat(result).isNull();
    }

    @Test
    void 회원가입시_비밀번호는_평문이_아닌_해시로_저장된다() throws Exception {
        String rawPassword = "password123";
        UserInfoDto pDTO = UserInfoDto.builder()
                .userId("newuser")
                .username("newuser")
                .password(rawPassword)
                .name("신규유저")
                .email("new@example.com")
                .build();
        when(userInfoRepository.save(any(UserInfoEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userInfoRepository.findByUserId("newuser"))
                .thenReturn(Optional.of(UserInfoEntity.builder().userId("newuser").build()));

        userInfoService.signup(pDTO);

        verify(userInfoRepository).save(argThat(entity ->
                entity.getPassword().equals(EncryptUtil.encryptSHA256(rawPassword))
                        && !entity.getPassword().equals(rawPassword)
        ));
    }

    @Test
    void 회원탈퇴시_비밀번호가_일치하지_않으면_실패한다() throws Exception {
        UserInfoEntity entity = UserInfoEntity.builder()
                .userId("testuser")
                .password(EncryptUtil.encryptSHA256("correctPassword"))
                .build();
        when(userInfoRepository.findByUserId("testuser")).thenReturn(Optional.of(entity));

        boolean result = userInfoService.deleteUser("testuser", "wrongPassword");

        assertThat(result).isFalse();
        verify(userInfoRepository, never()).delete(any());
    }

    @Test
    void 회원탈퇴시_비밀번호가_일치하면_삭제된다() throws Exception {
        UserInfoEntity entity = UserInfoEntity.builder()
                .userId("testuser")
                .password(EncryptUtil.encryptSHA256("correctPassword"))
                .build();
        when(userInfoRepository.findByUserId("testuser")).thenReturn(Optional.of(entity));

        boolean result = userInfoService.deleteUser("testuser", "correctPassword");

        assertThat(result).isTrue();
        verify(userInfoRepository).delete(entity);
    }

    @Test
    void 인증번호는_6자리_숫자_형식이다() throws Exception {
        when(userInfoRepository.findByEmail(any())).thenReturn(Optional.empty());

        String code = userInfoService.checkEmailAndSendCode("new@example.com");

        assertThat(code).matches("\\d{6}");
    }
}
