package com.ithopeschool.projectbe.domain.settings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.settings.dto.SettingsResponse;
import com.ithopeschool.projectbe.domain.settings.dto.SettingsUpdateRequest;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 "요청 모양 검사" 와 "응답 모양·상태 번호" 를 확인합니다. 진짜 로직(SettingsService)은 가짜로 바꿉니다.
@WebMvcTest(SettingsController.class)
class SettingsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SettingsService settingsService;

  // 처음 값 그대로인 설정
  private static final SettingsResponse DEFAULTS =
      new SettingsResponse(
          "21:00", 3, "23:00", "07:00", 3, false, 2, null, null, List.of(), null, null, Map.of());

  private ResultActions sendPatch(String body) throws Exception {
    return mockMvc.perform(
        patch("/api/v1/settings").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(ResultActions result, String message) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(settingsService);
  }

  // ---- 보기 ----

  @Test
  @DisplayName("보기는 200 이고, FE 의 이름으로 원래 열 칸 + #227 에서 더한 세 칸이 있다")
  void getSettings() throws Exception {
    given(settingsService.getSettings())
        .willReturn(
            new SettingsResponse(
                "21:00",
                3,
                "23:00",
                "07:00",
                3,
                true,
                2,
                4,
                LocalDate.of(2026, 10, 18),
                List.of("유튜브", "인스타그램"),
                5,
                LocalDate.of(2026, 10, 18),
                Map.of(
                    "dailyCount",
                    new SettingsResponse.SourceInfo(ChangeSource.USER, "내가 정했어요", true))));

    mockMvc
        .perform(get("/api/v1/settings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.callTime").value("21:00"))
        .andExpect(jsonPath("$.dailyCount").value(3))
        .andExpect(jsonPath("$.quietStart").value("23:00"))
        .andExpect(jsonPath("$.quietEnd").value("07:00"))
        .andExpect(jsonPath("$.maxHarshness").value(3))
        .andExpect(jsonPath("$.allowFocusMode").value(true))
        .andExpect(jsonPath("$.maxCareLevel").value(2))
        .andExpect(jsonPath("$.pendingMaxCareLevel").value(4))
        .andExpect(jsonPath("$.pendingEffectiveDate").value("2026-10-18"))
        .andExpect(jsonPath("$.lockApps[0]").value("유튜브"))
        .andExpect(jsonPath("$.lockApps[1]").value("인스타그램"))
        .andExpect(jsonPath("$.pendingMaxHarshness").value(5))
        .andExpect(jsonPath("$.pendingHarshnessEffectiveDate").value("2026-10-18"))
        .andExpect(jsonPath("$.sources.dailyCount.source").value("USER"))
        .andExpect(jsonPath("$.sources.dailyCount.reason").value("내가 정했어요"))
        .andExpect(jsonPath("$.sources.dailyCount.locked").value(true))
        .andExpect(jsonPath("$.length()").value(13))
        .andExpect(jsonPath("$.userId").doesNotExist());
  }

  @Test
  @DisplayName("바뀔 한도가 없으면 두 칸은 빼지 않고 null 로 보낸다")
  void pendingIsNull() throws Exception {
    given(settingsService.getSettings()).willReturn(DEFAULTS);

    mockMvc
        .perform(get("/api/v1/settings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pendingMaxCareLevel").isEmpty())
        .andExpect(jsonPath("$.pendingEffectiveDate").isEmpty())
        .andExpect(jsonPath("$.lockApps").isArray())
        .andExpect(jsonPath("$.lockApps.length()").value(0));
  }

  @Test
  @DisplayName("로그인하지 않았으면 Service 가 던진 401 이 그대로 나간다")
  void unauthorized() throws Exception {
    given(settingsService.getSettings())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    mockMvc
        .perform(get("/api/v1/settings"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  // ---- 바꾸기 ----

  @Test
  @DisplayName("바꾸기는 200 이고, 보낸 칸만 담아 Service 에 넘긴다 (앱 이름의 앞뒤 공백은 지움)")
  void update() throws Exception {
    given(settingsService.update(any())).willReturn(DEFAULTS);

    sendPatch(
            """
            {"maxHarshness":5,"quietStart":"22:30","lockApps":["  유튜브  ","게임"]}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.callTime").value("21:00"));
    verify(settingsService)
        .update(
            new SettingsUpdateRequest(
                null, null, "22:30", null, 5, null, null, List.of("유튜브", "게임")));
  }

  @Test
  @DisplayName("범위의 양 끝(메시지 0·5, 독설 1·5, 한도 0·4)과 00:00·23:59 는 된다")
  void updateAcceptsEdges() throws Exception {
    given(settingsService.update(any())).willReturn(DEFAULTS);

    for (String body :
        new String[] {
          "{\"dailyCount\":0}",
          "{\"dailyCount\":5}",
          "{\"maxHarshness\":1}",
          "{\"maxHarshness\":5}",
          "{\"maxCareLevel\":0}",
          "{\"maxCareLevel\":4}",
          "{\"callTime\":\"00:00\"}",
          "{\"quietEnd\":\"23:59\"}",
          "{\"lockApps\":[]}",
          "{\"allowFocusMode\":true}"
        }) {
      sendPatch(body).andExpect(status().isOk());
    }
  }

  // { 보낼 JSON, 돌아와야 할 문장 }
  static Stream<Arguments> wrongRequests() {
    String twentyOne =
        String.join(
            ",", Stream.iterate(1, i -> i + 1).limit(21).map(i -> "\"앱" + i + "\"").toList());
    return Stream.of(
        Arguments.of("{\"callTime\":\"9:00\"}", "전화 시간은 21:00 처럼 보내 주세요"),
        Arguments.of("{\"callTime\":\"24:00\"}", "전화 시간은 21:00 처럼 보내 주세요"),
        Arguments.of("{\"dailyCount\":-1}", "하루 메시지 횟수는 0~5 사이로 보내 주세요"),
        Arguments.of("{\"dailyCount\":6}", "하루 메시지 횟수는 0~5 사이로 보내 주세요"),
        Arguments.of("{\"quietStart\":\"11pm\"}", "연락 금지 시각은 23:00 처럼 보내 주세요"),
        Arguments.of("{\"quietEnd\":\"07:60\"}", "연락 금지 시각은 23:00 처럼 보내 주세요"),
        // 독설 수위는 1~5 만 받는다 (이슈 #22 완료 조건)
        Arguments.of("{\"maxHarshness\":0}", "독설 수위는 1~5 사이로 보내 주세요"),
        Arguments.of("{\"maxHarshness\":6}", "독설 수위는 1~5 사이로 보내 주세요"),
        Arguments.of("{\"maxCareLevel\":-1}", "제재 한도는 0~4 사이로 보내 주세요"),
        Arguments.of("{\"maxCareLevel\":5}", "제재 한도는 0~4 사이로 보내 주세요"),
        Arguments.of("{\"lockApps\":[\"   \"]}", "앱 이름을 입력해 주세요"),
        Arguments.of("{\"lockApps\":[\"" + "가".repeat(31) + "\"]}", "앱 이름은 30자 이하로 적어 주세요"),
        Arguments.of("{\"lockApps\":[" + twentyOne + "]}", "잠금 대상 앱은 20개까지 고를 수 있어요"),
        Arguments.of("{\"allowFocusMode\":\"yes\"}", "보낸 값을 다시 확인해 주세요"),
        Arguments.of("{\"maxHarshness\":", "보낸 값을 다시 확인해 주세요"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("wrongRequests")
  @DisplayName("바꾸기 요청이 틀리면 400 이고, Service 는 부르지 않는다")
  void updateRejected(String body, String message) throws Exception {
    expectRejected(sendPatch(body), message);
  }

  @Test
  @DisplayName("아무 칸도 안 보냈거나 앱이 겹치면 Service 가 던진 400 이 그대로 나간다")
  void updateServiceRejects() throws Exception {
    given(settingsService.update(any()))
        .willThrow(new ApiException(ErrorCode.VALIDATION_ERROR, "바꿀 값을 보내 주세요"));

    sendPatch("{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("바꿀 값을 보내 주세요"));
  }

  @Test
  @DisplayName("지원하지 않는 요청 방법(PUT)은 405")
  void methodNotAllowed() throws Exception {
    mockMvc
        .perform(put("/api/v1/settings").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isMethodNotAllowed());
    verifyNoInteractions(settingsService);
  }
}
