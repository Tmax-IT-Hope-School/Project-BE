package com.ithopeschool.projectbe.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 서버 전체를 진짜로 켜고(가짜 없이) 한 사람의 계정이 지나가는 길을 처음부터 끝까지 따라가 봅니다.
//   가입 → 로그인(기기 두 대) → 내 정보 → 고치기 → 비밀번호 바꾸기 → 로그아웃 → 다시 로그인 → 탈퇴 → 다시 가입
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AccountFlowTest {

  private static final String EMAIL = "account-flow@starflow.com";

  @Autowired private MockMvc mockMvc;
  @Autowired private AccountRepository accountRepository;

  private static String signupBody(String password) {
    return """
        {
          "email": "%s",
          "password": "%s",
          "name": "김티맥",
          "nickname": "티맥이",
          "schoolCode": "7530560",
          "schoolName": "한국디지털미디어고등학교",
          "consents": { "terms": true, "privacy": true, "diaryAnalysis": false, "over14": true, "version": "v1" }
        }
        """
        .formatted(EMAIL, password);
  }

  // 로그인해서 토큰을 받아 옵니다. 실패하면 null 입니다.
  private String login(String password) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, password)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return body.contains("\"token\":\"") ? body.split("\"token\":\"")[1].split("\"")[0] : null;
  }

  // 이 토큰을 Authorization 헤더에 붙이고, 본문이 있으면 JSON 으로 담습니다.
  private static MockHttpServletRequestBuilder with(
      MockHttpServletRequestBuilder builder, String token, String body) {
    builder.header("Authorization", "Bearer " + token);
    return body == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  @Test
  @DisplayName("가입부터 탈퇴까지: 고치기 · 비밀번호 바꾸기 · 로그아웃 · 탈퇴가 이어서 동작한다")
  void wholeLifeOfAnAccount(CapturedOutput output) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody("firstPass2026")))
        .andExpect(status().isCreated());
    String phone = login("firstPass2026");
    String laptop = login("firstPass2026");

    // 내 정보 보기
    mockMvc
        .perform(with(get("/api/v1/auth/profile"), phone, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(EMAIL))
        .andExpect(jsonPath("$.nickname").value("티맥이"));

    // 고치기: 닉네임을 바꾸고, 학교를 지우고, 일기 분석에 동의
    mockMvc
        .perform(
            with(
                put("/api/v1/auth/profile"),
                phone,
                "{\"name\":\"김티맥\",\"nickname\":\"새별명\",\"schoolCode\":null,\"schoolName\":null,\"diaryAnalysis\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nickname").value("새별명"))
        .andExpect(jsonPath("$.schoolName").doesNotExist())
        .andExpect(jsonPath("$.diaryAnalysis").value(true));
    // 로그인한 사람의 이름(/auth/me)도 새 닉네임으로 나옵니다.
    mockMvc
        .perform(with(get("/api/v1/auth/me"), laptop, null))
        .andExpect(jsonPath("$.name").value("새별명"));

    // 비밀번호 바꾸기: 틀린 비밀번호는 400 이고 로그인이 풀리지 않습니다.
    mockMvc
        .perform(
            with(
                put("/api/v1/auth/password"),
                phone,
                "{\"currentPassword\":\"wrongPass2026\",\"newPassword\":\"secondPass2026\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("비밀번호가 맞지 않아요"));
    mockMvc.perform(with(get("/api/v1/auth/me"), phone, null)).andExpect(status().isOk());

    mockMvc
        .perform(
            with(
                put("/api/v1/auth/password"),
                phone,
                "{\"currentPassword\":\"firstPass2026\",\"newPassword\":\"secondPass2026\"}"))
        .andExpect(status().isNoContent());
    // 바꾼 기기(phone)는 그대로, 다른 기기(laptop)는 로그인이 풀립니다.
    mockMvc.perform(with(get("/api/v1/auth/me"), phone, null)).andExpect(status().isOk());
    mockMvc
        .perform(with(get("/api/v1/auth/me"), laptop, null))
        .andExpect(status().isUnauthorized());
    assertThat(login("firstPass2026")).isNull();

    // 로그아웃: 그 토큰으로는 더 부를 수 없습니다.
    mockMvc
        .perform(with(post("/api/v1/auth/logout"), phone, null))
        .andExpect(status().isNoContent());
    mockMvc.perform(with(get("/api/v1/auth/me"), phone, null)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(with(post("/api/v1/auth/logout"), phone, null))
        .andExpect(status().isUnauthorized());

    // 다시 로그인해서 탈퇴: 비밀번호가 틀리면 안 되고, 맞으면 계정이 사라집니다.
    String again = login("secondPass2026");
    mockMvc
        .perform(with(post("/api/v1/auth/withdraw"), again, "{\"password\":\"firstPass2026\"}"))
        .andExpect(status().isBadRequest());
    assertThat(accountRepository.existsByEmail(EMAIL)).isTrue();

    mockMvc
        .perform(with(post("/api/v1/auth/withdraw"), again, "{\"password\":\"secondPass2026\"}"))
        .andExpect(status().isNoContent());
    assertThat(accountRepository.existsByEmail(EMAIL)).isFalse();
    mockMvc.perform(with(get("/api/v1/auth/me"), again, null)).andExpect(status().isUnauthorized());
    assertThat(login("secondPass2026")).isNull();

    // 탈퇴한 이메일로 다시 가입할 수 있습니다.
    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody("thirdPass2026")))
        .andExpect(status().isCreated());

    // 비밀번호와 이메일은 서버 로그에 남지 않습니다.
    assertThat(output).doesNotContain("firstPass2026", "secondPass2026", "thirdPass2026", EMAIL);
  }

  @Test
  @DisplayName("내 컴퓨터의 테스트 사용자(테스트 토큰)는 이메일 계정이 없어서 내 정보가 404 다")
  void testUserHasNoAccount() throws Exception {
    mockMvc
        .perform(with(get("/api/v1/auth/profile"), "local-test-token", null))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.message").value("이메일로 가입한 계정이 없어요"));
    // 테스트 토큰은 DB 에 없는 토큰이라 로그아웃해도 지울 것이 없고, 에러 없이 204 입니다.
    mockMvc
        .perform(with(post("/api/v1/auth/logout"), "local-test-token", null))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(with(get("/api/v1/auth/me"), "local-test-token", null))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("학년 · 반은 서버에 저장돼서, 다른 기기로 로그인해도 같은 값을 받는다 (둘 다 null 이면 지움)")
  void schoolClassFollowsTheAccount() throws Exception {
    String email = "school-class-flow@starflow.com";
    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody("classPass2026").replace(EMAIL, email)))
        .andExpect(status().isCreated());
    String loginBody = "{\"email\":\"%s\",\"password\":\"classPass2026\"}".formatted(email);
    String phone = tokenOf(loginBody);
    String laptop = tokenOf(loginBody);

    // 처음에는 비어 있습니다.
    mockMvc
        .perform(with(get("/api/v1/auth/school-class"), phone, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.grade").isEmpty())
        .andExpect(jsonPath("$.classNo").isEmpty());

    // 휴대폰에서 2학년 1반을 저장하면, 노트북에서도 같은 값을 받습니다.
    mockMvc
        .perform(with(put("/api/v1/auth/school-class"), phone, "{\"grade\":2,\"classNo\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.grade").value(2))
        .andExpect(jsonPath("$.classNo").value(1));
    mockMvc
        .perform(with(get("/api/v1/auth/school-class"), laptop, null))
        .andExpect(jsonPath("$.grade").value(2))
        .andExpect(jsonPath("$.classNo").value(1));

    // 하나만 보내거나 범위를 넘으면 400 이고, 저장된 값은 그대로입니다.
    mockMvc
        .perform(with(put("/api/v1/auth/school-class"), phone, "{\"grade\":3,\"classNo\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("학년과 반은 같이 보내 주세요"));
    mockMvc
        .perform(with(put("/api/v1/auth/school-class"), phone, "{\"grade\":4,\"classNo\":31}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(with(get("/api/v1/auth/school-class"), phone, null))
        .andExpect(jsonPath("$.grade").value(2));

    // 둘 다 null 이면 지웁니다.
    mockMvc
        .perform(with(put("/api/v1/auth/school-class"), phone, "{\"grade\":null,\"classNo\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.grade").isEmpty());
  }

  // 이 로그인 본문으로 로그인해서 토큰을 받아 옵니다.
  private String tokenOf(String loginBody) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return body.split("\"token\":\"")[1].split("\"")[0];
  }

  @Test
  @DisplayName("토큰 없이는 일곱 주소 모두 401")
  void allRequireLogin() throws Exception {
    mockMvc.perform(get("/api/v1/auth/profile")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            put("/api/v1/auth/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"김티맥\",\"nickname\":\"티맥이\",\"schoolCode\":null,\"schoolName\":null,\"diaryAnalysis\":false}"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            put("/api/v1/auth/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"study2026\",\"newPassword\":\"newpass2026\"}"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/auth/school-class")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            put("/api/v1/auth/school-class")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"grade\":2,\"classNo\":1}"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/v1/auth/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"study2026\"}"))
        .andExpect(status().isUnauthorized());
  }
}
