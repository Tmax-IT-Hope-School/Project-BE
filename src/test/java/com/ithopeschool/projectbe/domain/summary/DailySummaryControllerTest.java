package com.ithopeschool.projectbe.domain.summary;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.job.JobKeyInterceptor;
import com.ithopeschool.projectbe.domain.job.JobWebConfig;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 지난 날짜의 하루 요약 다시 만들기 POST /api/v1/jobs/daily-summary/rebuild 를 확인합니다. (이슈 #34 ③)
// 서버가 설정한 작업 키는 test-secret 입니다. 키가 틀리면 아무것도 만들지 않아야 합니다.
@WebMvcTest(
    controllers = DailySummaryController.class,
    properties = "app.job.secret-key=test-secret")
// 문지기(키 확인)와 그 연결 설정도 함께 올려야 실제와 같은 조건으로 시험할 수 있습니다.
@Import({JobWebConfig.class, JobKeyInterceptor.class})
class DailySummaryControllerTest {

  private static final String URL = "/api/v1/jobs/daily-summary/rebuild";
  private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DailySummaryJob dailySummaryJob;

  @Test
  @DisplayName("키가 맞으면 그날 요약을 다시 만들고 날짜 · 사람 수를 돌려준다 (200)")
  void rebuildsWithKey() throws Exception {
    given(dailySummaryJob.rebuildPastDay(DAY)).willReturn(3);

    mockMvc
        .perform(post(URL).param("date", "2026-10-20").header("X-Job-Key", "test-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-10-20"))
        .andExpect(jsonPath("$.userCount").value(3))
        .andExpect(jsonPath("$.message").value("2026-10-20 하루 요약을 3명 다시 만들었어요"));
  }

  @Test
  @DisplayName("키가 없거나 틀리면 401 이고 아무것도 만들지 않는다")
  void rejectsWithoutKey() throws Exception {
    mockMvc
        .perform(post(URL).param("date", "2026-10-20"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    mockMvc
        .perform(post(URL).param("date", "2026-10-20").header("X-Job-Key", "wrong-key"))
        .andExpect(status().isUnauthorized());

    verify(dailySummaryJob, never()).rebuildPastDay(any());
  }

  @Test
  @DisplayName("날짜가 없거나 모양이 틀리면 400 이고 아무것도 만들지 않는다")
  void rejectsBadDate() throws Exception {
    mockMvc
        .perform(post(URL).header("X-Job-Key", "test-secret"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    mockMvc
        .perform(post(URL).param("date", "2026/10/20").header("X-Job-Key", "test-secret"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    verify(dailySummaryJob, never()).rebuildPastDay(any());
  }

  @Test
  @DisplayName("아직 오지 않은 날짜면 400 과 한국어 문장을 돌려준다")
  void rejectsFutureDate() throws Exception {
    given(dailySummaryJob.rebuildPastDay(DAY))
        .willThrow(
            new ApiException(
                ErrorCode.VALIDATION_ERROR, "아직 오지 않은 날짜는 만들 수 없어요. 오늘(2026-10-19)까지만 돼요"));

    mockMvc
        .perform(post(URL).param("date", "2026-10-20").header("X-Job-Key", "test-secret"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(
            jsonPath("$.error.message").value("아직 오지 않은 날짜는 만들 수 없어요. 오늘(2026-10-19)까지만 돼요"));
  }
}
