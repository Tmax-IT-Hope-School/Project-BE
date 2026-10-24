package com.ithopeschool.projectbe.domain.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.JobResultStore;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

// 예약 작업 daily-summary: 어제와 오늘을 사용자마다 다시 만드는지,
// 지난 날짜 다시 만들기 명령이 아직 오지 않은 날을 막는지 확인합니다.
class DailySummaryJobTest {

  // 지금 = 한국 시간 2026-10-21 03:30 → 하루의 기준(새벽 4시) 으로는 아직 10월 20일
  private static final Instant NOW = Instant.parse("2026-10-20T18:30:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
  private static final LocalDate YESTERDAY = LocalDate.of(2026, 10, 19);

  private final DailySummaryService service = mock(DailySummaryService.class);
  private final JobResultStore resultStore = mock(JobResultStore.class);
  private final DailySummaryJob job =
      new DailySummaryJob(service, resultStore, Clock.fixed(NOW, ZoneId.of("UTC")));

  @Test
  @DisplayName("한 번 돌면 어제 → 오늘 순서로, 그날 요약을 만들 사람마다 다시 만든다")
  void rebuildsYesterdayAndToday() {
    given(service.userIdsOn(YESTERDAY)).willReturn(List.of(1L));
    given(service.userIdsOn(TODAY)).willReturn(List.of(1L, 2L));

    job.run(new JobContext(TODAY, NOW));

    InOrder order = inOrder(service);
    order.verify(service).rebuild(1L, YESTERDAY);
    order.verify(service).rebuild(1L, TODAY);
    order.verify(service).rebuild(2L, TODAY);
    // 결과 글자에는 매번 달라지는 값(시각 등)이 없어서, 두 번 돌아도 같습니다.
    verify(resultStore).save(DailySummaryJob.NAME, TODAY, "하루 요약 2026-10-19 1명, 2026-10-20 2명");
  }

  @Test
  @DisplayName("③ 지난 날짜 다시 만들기: 그날 요약을 만들 사람 수를 돌려준다 (오늘까지 됨)")
  void rebuildsPastDay() {
    LocalDate past = LocalDate.of(2026, 10, 10);
    given(service.userIdsOn(past)).willReturn(List.of(1L, 2L, 3L));
    given(service.userIdsOn(TODAY)).willReturn(List.of(1L));

    assertThat(job.rebuildPastDay(past)).isEqualTo(3);
    assertThat(job.rebuildPastDay(TODAY)).isEqualTo(1);
    verify(service).rebuild(3L, past);
  }

  @Test
  @DisplayName("③ 지난 날짜 다시 만들기: 아직 오지 않은 날(새벽 4시 기준 내일)은 400 이고 아무것도 만들지 않는다")
  void rejectsFutureDay() {
    assertThatThrownBy(() -> job.rebuildPastDay(TODAY.plusDays(1)))
        .isInstanceOf(ApiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);

    verify(service, never()).rebuild(any(), any());
  }
}
