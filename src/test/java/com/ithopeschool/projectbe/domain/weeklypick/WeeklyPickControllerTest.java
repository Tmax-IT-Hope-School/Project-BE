package com.ithopeschool.projectbe.domain.weeklypick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.weeklypick.dto.WeeklyPickResponse;
import com.ithopeschool.projectbe.domain.weeklypick.dto.WeeklyPickSaveRequest;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 요청 모양 검사와 응답 상태 번호 · JSON 모양을 확인합니다. 진짜 로직(WeeklyPickService)은 가짜입니다.
@WebMvcTest(WeeklyPickController.class)
class WeeklyPickControllerTest {

  private static final LocalDate MONDAY = LocalDate.parse("2026-10-26");
  private static final String BODY =
      "{\"title\":\"폰을 다른 방에 두기\",\"hint\":\"손이 안 닿으면 덜 멈춰요\",\"skip\":false,\"decidedAt\":1792800000000}";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private WeeklyPickService weeklyPickService;

  // 계약서(FE 와 맞춘 모양)의 예시와 같은 응답
  private static WeeklyPickResponse sample() {
    return new WeeklyPickResponse(
        "5", MONDAY, "폰을 다른 방에 두기", "손이 안 닿으면 덜 멈춰요", false, 1792800000000L);
  }

  private ResultActions save(String weekStart, String body) throws Exception {
    return mockMvc.perform(
        put("/api/v1/weekly-picks/" + weekStart)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  @DisplayName("저장하면 200 과 계약서 모양(id 는 글자, decidedAt 은 밀리초 숫자)을 돌려준다")
  void saves() throws Exception {
    given(weeklyPickService.save(eq(MONDAY), any())).willReturn(sample());

    save("2026-10-26", BODY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("5"))
        .andExpect(jsonPath("$.weekStart").value("2026-10-26"))
        .andExpect(jsonPath("$.title").value("폰을 다른 방에 두기"))
        .andExpect(jsonPath("$.hint").value("손이 안 닿으면 덜 멈춰요"))
        .andExpect(jsonPath("$.skip").value(false))
        .andExpect(jsonPath("$.decidedAt").value(1792800000000L));
  }

  @Test
  @DisplayName("앞뒤 공백은 지워서 넘기고, decidedAt 은 안 보내도 된다 (60자 딱 맞으면 통과)")
  void trimsAndAllowsMissingDecidedAt() throws Exception {
    given(weeklyPickService.save(eq(MONDAY), any())).willReturn(sample());
    String sixty = "가".repeat(60);

    save("2026-10-26", "{\"title\":\"  " + sixty + "  \",\"hint\":\" 쉬어요 \",\"skip\":true}")
        .andExpect(status().isOk());

    ArgumentCaptor<WeeklyPickSaveRequest> captor =
        ArgumentCaptor.forClass(WeeklyPickSaveRequest.class);
    verify(weeklyPickService).save(eq(MONDAY), captor.capture());
    assertThat(captor.getValue().title()).isEqualTo(sixty);
    assertThat(captor.getValue().hint()).isEqualTo("쉬어요");
    assertThat(captor.getValue().skip()).isTrue();
    assertThat(captor.getValue().decidedAt()).isNull();
  }

  @ParameterizedTest(name = "{0} 이면 400")
  @ValueSource(
      strings = {
        "{\"hint\":\"설명\",\"skip\":false}",
        "{\"title\":\"   \",\"hint\":\"설명\",\"skip\":false}",
        "{\"title\":\"해 볼 것\",\"skip\":false}",
        "{\"title\":\"해 볼 것\",\"hint\":\"\",\"skip\":false}",
        "{\"title\":\"해 볼 것\",\"hint\":\"설명\"}",
        "{\"title\":\"해 볼 것\",\"hint\":\"설명\",\"skip\":false,\"decidedAt\":0}",
        "{\"title\":\"해 볼 것\",\"hint\":\"설명\",\"skip\":false,\"decidedAt\":-5}",
        "{\"title\":\"해 볼 것\",\"hint\":\"설명\",\"skip\":\"아니\"}",
        "{\"title\":"
      })
  @DisplayName("제목 · 설명이 비었거나, skip 이 없거나, 고른 시각이 0 이하거나, JSON 이 깨지면 400 이고 Service 는 부르지 않는다")
  void rejectsBadBody(String body) throws Exception {
    save("2026-10-26", body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(weeklyPickService);
  }

  @Test
  @DisplayName("제목 · 설명이 60자를 넘으면 400 과 한국어 안내")
  void rejectsTooLong() throws Exception {
    String tooLong = "가".repeat(61);

    save("2026-10-26", "{\"title\":\"" + tooLong + "\",\"hint\":\"설명\",\"skip\":false}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("해 볼 것은 60자까지 적을 수 있어요"));
    save("2026-10-26", "{\"title\":\"해 볼 것\",\"hint\":\"" + tooLong + "\",\"skip\":false}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("한 줄 설명은 60자까지 적을 수 있어요"));
    verifyNoInteractions(weeklyPickService);
  }

  @ParameterizedTest(name = "주소의 날짜가 {0} 이면 400")
  @ValueSource(strings = {"2026-13-40", "이번주", "2026-10-26T00:00"})
  @DisplayName("주소의 날짜 모양이 틀리면 400 이고 Service 는 부르지 않는다")
  void rejectsBadWeekStart(String weekStart) throws Exception {
    save(weekStart, BODY)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(weeklyPickService);
  }

  @Test
  @DisplayName("월요일이 아니면 Service 가 던진 400 이 그대로 나간다")
  void notMondayIsBadRequest() throws Exception {
    given(weeklyPickService.save(eq(MONDAY.plusDays(1)), any()))
        .willThrow(new ApiException(ErrorCode.VALIDATION_ERROR, "weekStart 는 그 주의 월요일 날짜로 보내 주세요"));

    save("2026-10-27", BODY)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("weekStart 는 그 주의 월요일 날짜로 보내 주세요"));
  }

  @Test
  @DisplayName("같은 순간에 다른 요청이 먼저 그 주의 줄을 만들었으면(겹침 에러) 한 번 더 불러 덮어쓴다")
  void retriesOnceWhenSameWeekWasJustCreated() throws Exception {
    given(weeklyPickService.save(eq(MONDAY), any()))
        .willThrow(new DataIntegrityViolationException("uk_weekly_picks_user_id_week_start"))
        .willReturn(sample());

    save("2026-10-26", BODY).andExpect(status().isOk()).andExpect(jsonPath("$.id").value("5"));
    verify(weeklyPickService, times(2)).save(eq(MONDAY), any());
  }

  @Test
  @DisplayName("로그인하지 않으면 401 이다 (Service 의 getCurrentUserId 가 던짐)")
  void unauthorized() throws Exception {
    given(weeklyPickService.list(MONDAY, MONDAY))
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    mockMvc
        .perform(get("/api/v1/weekly-picks").param("from", "2026-10-26").param("to", "2026-10-26"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("기간 목록은 200 과 배열")
  void lists() throws Exception {
    given(weeklyPickService.list(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31")))
        .willReturn(List.of(sample()));

    mockMvc
        .perform(get("/api/v1/weekly-picks").param("from", "2026-10-01").param("to", "2026-10-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value("5"))
        .andExpect(jsonPath("$[0].weekStart").value("2026-10-26"));
  }

  @ParameterizedTest(name = "{0} 이면 400")
  @ValueSource(strings = {"", "from=2026-10-01", "to=2026-10-31", "from=2026/10/01&to=2026-10-31"})
  @DisplayName("from · to 중 하나라도 없거나 모양이 틀리면 400 이고 Service 는 부르지 않는다")
  void rejectsBadListParams(String query) throws Exception {
    mockMvc
        .perform(get("/api/v1/weekly-picks?" + query))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(weeklyPickService);
  }

  @Test
  @DisplayName("시작이 끝보다 늦거나 366일을 넘으면 Service 가 던진 400 이 그대로 나간다")
  void rangeRuleErrorsAreBadRequest() throws Exception {
    given(weeklyPickService.list(MONDAY, MONDAY.minusDays(1)))
        .willThrow(new ApiException(ErrorCode.VALIDATION_ERROR, "시작 날짜는 끝 날짜보다 늦을 수 없어요"));

    mockMvc
        .perform(get("/api/v1/weekly-picks").param("from", "2026-10-26").param("to", "2026-10-25"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("시작 날짜는 끝 날짜보다 늦을 수 없어요"));
  }
}
