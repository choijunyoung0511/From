package com.from.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.from.domain.User;
import com.from.dto.user.SessionUser;
import com.from.mapper.UserMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/user/kakao")
@RequiredArgsConstructor
public class KakaoController {

    @Value("${kakao.client-id}")
    private String clientId;

    @Value("${kakao.client-secret}")
    private String clientSecret;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    private final UserMapper userMapper;

    // 카카오 로그인 시작
    @GetMapping("/login")
    public String kakaoLogin() {
        String kakaoAuthUrl = "https://kauth.kakao.com/oauth/authorize"
                + "?response_type=code"
                + "&client_id=" + clientId
                + "&redirect_uri=" + redirectUri;
        return "redirect:" + kakaoAuthUrl;
    }

    // ===== 카카오 로그인 콜백 =====
    @GetMapping("/callback")
    public String kakaoCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            HttpSession session) {

        if (error != null) {
            log.error("카카오 로그인 에러: {}", error);
            return "redirect:/user/login?error=kakao";
        }

        try {
            // 1. 인가코드로 액세스 토큰 발급
            KakaoTokenResponse tokenResponse = getAccessToken(code);
            log.info("카카오 토큰 발급 완료");

            // 2. 액세스 토큰으로 사용자 정보 조회
            KakaoUserInfoResponse userInfo = getUserInfo(tokenResponse.accessToken());
            Long kakaoId = userInfo.id();
            String nickname = userInfo.kakaoAccount().profile().nickname();
            log.info("카카오 사용자 정보 - kakaoId: {}, nickname: {}", kakaoId, nickname);

            // 3. DB에서 카카오 ID로 기존 회원 조회
            User user = userMapper.findByKakaoId(kakaoId);

            if (user == null) {
                // 4. 신규 회원 자동 가입
                user = new User();
                user.setKakaoId(kakaoId);
                user.setUsername("kakao_" + kakaoId);
                user.setName(nickname);
                user.setEmail("");           // 카카오는 이메일 비즈앱 필요 - 빈값
                user.setPassword("");        // 소셜 로그인은 비밀번호 없음
                user.setLoginType("KAKAO");
                userMapper.insertKakaoUser(user);
                log.info("카카오 신규 회원 가입 완료 - kakaoId: {}", kakaoId);
            }

            // 5. 세션 저장
            SessionUser sessionUser = new SessionUser();
            sessionUser.setUserId(user.getUserId());
            sessionUser.setUsername(user.getUsername());
            sessionUser.setName(user.getName());
            sessionUser.setEmail(user.getEmail());
            session.setAttribute("loginUser", sessionUser);

            log.info("카카오 로그인 성공 - {}", nickname);
            return "redirect:/service";

        } catch (Exception e) {
            log.error("카카오 로그인 처리 오류", e);
            return "redirect:/user/login?error=kakao";
        }
    }

    // ===== 액세스 토큰 발급 =====
    private KakaoTokenResponse getAccessToken(String authCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("redirect_uri", redirectUri);
        body.add("code", authCode);
        body.add("client_secret", clientSecret);

        HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(body, headers);

        ResponseEntity<KakaoTokenResponse> response = new RestTemplate().exchange(
                "https://kauth.kakao.com/oauth/token",
                HttpMethod.POST,
                httpEntity,
                KakaoTokenResponse.class);

        return response.getBody();
    }

    // ===== 사용자 정보 조회 =====
    private KakaoUserInfoResponse getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");
        headers.add("Authorization", "bearer " + accessToken);

        ResponseEntity<KakaoUserInfoResponse> response = new RestTemplate().exchange(
                "https://kapi.kakao.com/v2/user/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                KakaoUserInfoResponse.class);

        return response.getBody();
    }

    // ===== DTO =====
    public record KakaoTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("scope") String scope
    ) {}

    public record KakaoUserInfoResponse(
            @JsonProperty("id") Long id,
            @JsonProperty("connected_at") String connectedAt,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount,
            @JsonProperty("properties") Map<String, Object> properties
    ) {
        public record KakaoAccount(
                @JsonProperty("profile") Profile profile
        ) {
            public record Profile(
                    @JsonProperty("nickname") String nickname,
                    @JsonProperty("profile_image_url") String profileImageUrl,
                    @JsonProperty("thumbnail_image_url") String thumbnailImageUrl
            ) {}
        }
    }
}