package com.ithopeschool.projectbe.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.global.time.DayRule;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 서버 전체를 진짜로 켜고(가짜 없이) 관리 설정 API 를 처음부터 끝까지 불러 봅니다.
// 새 사용자 두 명(에이·비이)을 가입시켜서 "남의 설정이 보이거나 바뀌지 않는지" 도 같이 봅니다.
// 끝나면 두 사람을 탈퇴시켜서 만든 데이터를 모두 지웁니다. (탈퇴가 설정·앱·기록까지 지우는지도 확인됩니다)
@SpringBootTest
@AutoConfigureMockMvc
class SettingsFlowTest {

  private static final String PASSWORD = "study2026";

  @Autowired private MockMvc mockMvc;
  @Autowired private CareSettingRepository careSettingRepository;
  @Autowired private FocusLockAppRepository focusLockAppRepository;
  @Autowired private SettingChangeRepository settingChangeRepository;

  private String tokenA;
  private String tokenB;
  private Long userIdA;
  private Long userIdB;

  @BeforeEach
  void signUpTwoUsers() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String emailA = "settings-a-" + suffix + "@starflow.com";
    String emailB = "settings-b-" + suffix + "@starflow.com";
    userIdA = signup(emailA, "에이에이");
    userIdB = signup(emailB, "비이비이");
    tokenA = login(emailA);
    tokenB = login(emailB);
  }

  @AfterEach
  void withdrawBoth() throws Exception {
    send(post("/api/v1/auth/withdraw"), tokenA, "{\"password\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());
    send(post("/api/v1/auth/withdraw"), tokenB, "{\"password\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());
    // 탈퇴하면 설정·잠금 대상 앱·바꾼 기록도 함께 지워집니다.
    for (Long userId : new Long[] {userIdA, userIdB}) {
      assertThat(careSettingRepository.findByUserId(userId)).isEmpty();
      assertThat(focusLockAppRepository.findByUserIdOrderById(userId)).isEmpty();
      assertThat(settingChangeRepository.findByUserIdOrderById(userId)).isEmpty();
    }
  }

  // ---- 도우미 ----

  private Long signup(String email, String nickname) throws Exception {
    String body =
        """
        {
          "email": "%s",
          "password": "%s",
          "name": "김티맥",
          "nickname": "%s",
          "schoolCode": null,
          "schoolName": null,
          "consents": { "terms": true, "privacy": true, "diaryAnalysis": false, "over14": true, "version": "v1" }
        }
        """
            .formatted(email, PASSWORD, nickname);
    ResultActions result = send(post("/api/v1/auth/signup"), null, body);
    result.andExpect(status().isCreated());
    return Long.valueOf(JsonPath.read(responseBody(result), "$.id"));
  }

  private String login(String email) throws Exception {
    ResultActions result =
        send(
            post("/api/v1/auth/login"),
            null,
            "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD));
    result.andExpect(status().isOk());
    return JsonPath.read(responseBody(result), "$.token");
  }

  // 이 토큰을 Authorization 헤더에 붙이고(null 이면 로그인 안 한 요청), 본문이 있으면 JSON 으로 담아 부릅니다.
  private ResultActions send(MockHttpServletRequestBuilder builder, String token, String body)
      throws Exception {
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    if (body != null) {
      builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return mockMvc.perform(builder);
  }

  private static String responseBody(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  // ---- 시나리오 ----

  @Test
  @DisplayName("로그인하지 않으면 두 주소 모두 401")
  void requiresLogin() throws Exception {
    send(get("/api/v1/settings"), null, null).andExpect(status().isUnauthorized());
    send(patch("/api/v1/settings"), null, "{\"maxHarshness\":5}")
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("처음 값 보기 → 바꾸기(기록 남음) → 한도 올리기(내일부터) → 낮추기(즉시) → 틀린 값 거절")
  void viewUpdateAndRecord() throws Exception {
    // 처음 값 (연락 금지 기본은 밤 11시 ~ 아침 7시)
    send(get("/api/v1/settings"), tokenA, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.callTime").value("21:00"))
        .andExpect(jsonPath("$.dailyCount").value(3))
        .andExpect(jsonPath("$.quietStart").value("23:00"))
        .andExpect(jsonPath("$.quietEnd").value("07:00"))
        .andExpect(jsonPath("$.maxHarshness").value(2))
        .andExpect(jsonPath("$.allowFocusMode").value(false))
        .andExpect(jsonPath("$.maxCareLevel").value(3))
        .andExpect(jsonPath("$.pendingMaxCareLevel").isEmpty())
        .andExpect(jsonPath("$.lockApps.length()").value(0));

    // 바꾸기: 보낸 칸만 바뀝니다.
    send(
            patch("/api/v1/settings"),
            tokenA,
            "{\"maxHarshness\":1,\"quietStart\":\"22:30\",\"allowFocusMode\":true,"
                + "\"lockApps\":[\"유튜브\",\"게임\"]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maxHarshness").value(1))
        .andExpect(jsonPath("$.quietStart").value("22:30"))
        .andExpect(jsonPath("$.quietEnd").value("07:00"))
        .andExpect(jsonPath("$.allowFocusMode").value(true))
        .andExpect(jsonPath("$.lockApps[0]").value("유튜브"))
        .andExpect(jsonPath("$.lockApps[1]").value("게임"))
        // 보낸 칸은 "내가 정함" 으로 잠기고, 안 보낸 칸은 근거가 비어 있다 (#227)
        .andExpect(jsonPath("$.sources.maxHarshness.source").value("USER"))
        .andExpect(jsonPath("$.sources.maxHarshness.reason").value("내가 정했어요"))
        .andExpect(jsonPath("$.sources.maxHarshness.locked").value(true))
        .andExpect(jsonPath("$.sources.callTime.source").isEmpty())
        .andExpect(jsonPath("$.sources.callTime.locked").value(false));

    // 한도 올리기: 오늘은 3 그대로, 내일부터 4
    String tomorrow = DayRule.dateOf(Instant.now()).plusDays(1).toString();
    send(patch("/api/v1/settings"), tokenA, "{\"maxCareLevel\":4}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maxCareLevel").value(3))
        .andExpect(jsonPath("$.pendingMaxCareLevel").value(4))
        .andExpect(jsonPath("$.pendingEffectiveDate").value(tomorrow));

    // 한도 낮추기: 바로 1, 올리기는 취소
    send(patch("/api/v1/settings"), tokenA, "{\"maxCareLevel\":1}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maxCareLevel").value(1))
        .andExpect(jsonPath("$.pendingMaxCareLevel").isEmpty());

    // 잠금 대상 앱을 같은 앱 포함으로 다시 바꿔도 된다 (지우고 바로 다시 넣기)
    send(patch("/api/v1/settings"), tokenA, "{\"lockApps\":[\"게임\"]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lockApps.length()").value(1))
        .andExpect(jsonPath("$.lockApps[0]").value("게임"));

    // 틀린 값은 400 이고 아무것도 바뀌지 않습니다.
    for (String body :
        new String[] {
          "{}",
          "{\"maxHarshness\":0}",
          "{\"maxHarshness\":6}",
          "{\"dailyCount\":6}",
          "{\"maxCareLevel\":5}",
          "{\"quietStart\":\"7:00\"}",
          "{\"lockApps\":[\"유튜브\",\"유튜브\"]}",
          "{\"maxHarshness\":2,\"lockApps\":[\"유튜브\",\"유튜브\"]}"
        }) {
      send(patch("/api/v1/settings"), tokenA, body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
    send(get("/api/v1/settings"), tokenA, null)
        .andExpect(jsonPath("$.maxHarshness").value(1))
        .andExpect(jsonPath("$.lockApps[0]").value("게임"));

    // 바꾼 기록: 언제 · 무엇을 · 전 값 · 새 값 (틀린 요청은 기록되지 않음)
    assertThat(settingChangeRepository.findByUserIdOrderById(userIdA))
        .extracting(SettingChange::getField, SettingChange::getOldValue, SettingChange::getNewValue)
        .containsExactly(
            tuple("quietStart", "23:00", "22:30"),
            tuple("maxHarshness", "2", "1"),
            tuple("allowFocusMode", "false", "true"),
            tuple("lockApps", "", "유튜브, 게임"),
            tuple("maxCareLevel", "3", "4"),
            tuple("maxCareLevel", "4", "1"),
            tuple("lockApps", "유튜브, 게임", "게임"));
    assertThat(settingChangeRepository.findByUserIdOrderById(userIdA))
        .allSatisfy(
            change -> {
              assertThat(change.getChangedAt()).isNotNull();
              assertThat(change.getSource()).isEqualTo(ChangeSource.USER);
            });
  }

  @Test
  @DisplayName("남의 설정은 보이지도, 바뀌지도 않는다")
  void othersSettingsAreSeparate() throws Exception {
    send(patch("/api/v1/settings"), tokenA, "{\"maxHarshness\":1,\"lockApps\":[\"유튜브\"]}")
        .andExpect(status().isOk());

    // 비이는 여전히 처음 값이고, 에이의 앱이 보이지 않습니다.
    send(get("/api/v1/settings"), tokenB, null)
        .andExpect(jsonPath("$.maxHarshness").value(2))
        .andExpect(jsonPath("$.lockApps.length()").value(0));

    // 비이가 같은 앱을 넣어도 됩니다. (같은 앱 금지는 사람마다)
    send(patch("/api/v1/settings"), tokenB, "{\"maxHarshness\":4,\"lockApps\":[\"유튜브\"]}")
        .andExpect(status().isOk());

    // 에이는 그대로입니다.
    send(get("/api/v1/settings"), tokenA, null)
        .andExpect(jsonPath("$.maxHarshness").value(1))
        .andExpect(jsonPath("$.lockApps.length()").value(1));
    assertThat(settingChangeRepository.findByUserIdOrderById(userIdB))
        .extracting(SettingChange::getField)
        .containsExactly("maxHarshness", "lockApps");
  }
}
