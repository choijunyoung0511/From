package com.from.controller;

import com.from.service.IBookService;
import com.from.service.IImageService;
import com.from.service.IReviewService;
import com.from.service.IS3UploadService;
import com.from.service.IUserInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
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

    @BeforeEach
    void setUp() {
        userInfoService = mock(IUserInfoService.class);
        UserInfoController controller = new UserInfoController(
                userInfoService,
                mock(IReviewService.class),
                mock(IBookService.class),
                mock(IImageService.class),
                mock(IS3UploadService.class)
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
