package com.from.controller;

import com.from.dto.UserInfoDto;
import com.from.service.IBookService;
import com.from.service.IImageService;
import com.from.service.IReviewService;
import com.from.service.IS3UploadService;
import com.from.service.IUserInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * DB/Redis/Mongo 없이(standalone MockMvc) 인증코드 brute-force 방어와
 * 비밀번호 찾기 인증 우회 방지 로직만 검증한다.
 */
class UserInfoControllerSecurityTest {

    private MockMvc mockMvc;
    private IUserInfoService userInfoService;
    // 실제 Redis 없이 opsForValue().get/increment/delete 동작을 흉내내는 인메모리 스토어
    private final Map<String, String> fakeRedisStore = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        userInfoService = mock(IUserInfoService.class);

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenAnswer(inv -> fakeRedisStore.get(inv.getArgument(0, String.class)));
        when(valueOps.increment(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0, String.class);
            long next = Long.parseLong(fakeRedisStore.getOrDefault(key, "0")) + 1;
            fakeRedisStore.put(key, String.valueOf(next));
            return next;
        });
        when(redisTemplate.delete(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0, String.class);
            return fakeRedisStore.remove(key) != null;
        });

        UserInfoController controller = new UserInfoController(
                userInfoService,
                mock(IReviewService.class),
                mock(IBookService.class),
                mock(IImageService.class),
                mock(IS3UploadService.class),
                redisTemplate
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void 이메일_인증코드_5회_실패후_6번째_시도는_잠금처리된다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("emailCode", "123456");
        session.setAttribute("emailCodeTime", java.time.LocalDateTime.now());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/user/verifyEmailCode")
                            .session(session)
                            .param("code", "000000"))
                    .andExpect(jsonPath("$.result").value(0))
                    .andExpect(jsonPath("$.msg").value("인증번호가 틀립니다."));
        }

        // 6번째 시도: 정답 코드를 넣어도 이미 잠긴 상태라 실패해야 한다
        mockMvc.perform(post("/user/verifyEmailCode")
                        .session(session)
                        .param("code", "123456"))
                .andExpect(jsonPath("$.result").value(0))
                .andExpect(jsonPath("$.msg", containsString("시도 횟수를 초과")));
    }

    @Test
    void 이메일_인증코드가_맞으면_시도횟수와_무관하게_성공한다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("emailCode", "123456");
        session.setAttribute("emailCodeTime", java.time.LocalDateTime.now());

        mockMvc.perform(post("/user/verifyEmailCode")
                        .session(session)
                        .param("code", "123456"))
                .andExpect(jsonPath("$.result").value(1));
    }

    @Test
    void 비밀번호찾기_인증검증을_거치지_않으면_비밀번호_변경이_거부된다() throws Exception {
        // findPwUserId만 세팅되고 findPwVerified가 없는 상태
        // (= 인증번호 검증 단계를 건너뛰고 change를 바로 호출하는 시나리오)
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("findPwUserId", "victim");

        mockMvc.perform(post("/user/findPassword/change")
                        .session(session)
                        .param("newPassword", "newPassword123!"))
                .andExpect(jsonPath("$.result").value(0))
                .andExpect(jsonPath("$.msg", containsString("인증번호 확인을 먼저 완료")));
    }

    @Test
    void 비밀번호찾기_인증검증완료후에는_비밀번호_변경이_허용된다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("findPwUserId", "victim");
        session.setAttribute("findPwVerified", true);
        when(userInfoService.changePasswordByUsername(any(), any())).thenReturn(true);

        mockMvc.perform(post("/user/findPassword/change")
                        .session(session)
                        .param("newPassword", "newPassword123!"))
                .andExpect(jsonPath("$.result").value(1));
    }

    @Test
    void 회원가입시_비밀번호_강도가_약하면_거부된다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("emailVerified", true);

        mockMvc.perform(post("/user/signup")
                        .session(session)
                        .param("userId", "testuser")
                        .param("username", "testuser")
                        .param("password", "1234") // 너무 짧고 숫자만 있음
                        .param("name", "테스트")
                        .param("email", "test@example.com"))
                .andExpect(jsonPath("$.result").value(0))
                .andExpect(jsonPath("$.msg", containsString("비밀번호는")));
    }

    @Test
    void 로그인_5회_실패후_6번째_시도는_비밀번호가_맞아도_잠금처리된다() throws Exception {
        when(userInfoService.login("victim", "wrongPassword")).thenReturn(null);
        when(userInfoService.login("victim", "correctPassword")).thenReturn(
                UserInfoDto.builder().userId("victim").name("피해자").build());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/user/loginProc")
                            .param("userId", "victim")
                            .param("password", "wrongPassword"))
                    .andExpect(jsonPath("$.result").value(0));
        }

        // 6번째 시도: 이번엔 정답 비밀번호를 넣어도 잠금 상태라 거부되어야 한다
        mockMvc.perform(post("/user/loginProc")
                        .param("userId", "victim")
                        .param("password", "correctPassword"))
                .andExpect(jsonPath("$.result").value(0))
                .andExpect(jsonPath("$.msg", containsString("시도 횟수를 초과")));
    }

    @Test
    void 로그인_성공시_실패카운트가_초기화된다() throws Exception {
        when(userInfoService.login("user2", "wrongPassword")).thenReturn(null);
        when(userInfoService.login("user2", "correctPassword")).thenReturn(
                UserInfoDto.builder().userId("user2").name("사용자").build());

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/user/loginProc")
                            .param("userId", "user2")
                            .param("password", "wrongPassword"))
                    .andExpect(jsonPath("$.result").value(0));
        }

        // 5번째 시도(마지막 허용 범위)에 성공 → 카운트 초기화되어야 함
        mockMvc.perform(post("/user/loginProc")
                        .param("userId", "user2")
                        .param("password", "correctPassword"))
                .andExpect(jsonPath("$.result").value(1));

        // 초기화 이후 다시 실패해도 즉시 잠기지 않아야 한다
        mockMvc.perform(post("/user/loginProc")
                        .param("userId", "user2")
                        .param("password", "wrongPassword"))
                .andExpect(jsonPath("$.result").value(0))
                .andExpect(jsonPath("$.msg", org.hamcrest.Matchers.not(containsString("시도 횟수를 초과"))));
    }

    @Test
    void 회원가입시_이메일_형식이_아니면_거부된다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("emailVerified", true);

        mockMvc.perform(post("/user/signup")
                        .session(session)
                        .param("userId", "testuser")
                        .param("username", "testuser")
                        .param("password", "password123!")
                        .param("name", "테스트")
                        .param("email", "not-an-email"))
                .andExpect(jsonPath("$.result").value(0))
                .andExpect(jsonPath("$.msg", containsString("이메일 형식")));
    }
}
