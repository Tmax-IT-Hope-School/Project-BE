package com.ithopeschool.projectbe.domain.variant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.variant.dto.DailyResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Service 단위 테스트: GET daily 는 조회지만 과제를 새로 배정(저장)하므로,
// 최신 동의 문구에 동의한 사람에게만 배정하는지 확인합니다. (이슈 #71 의 규칙)
// VariantFlowTest 는 로컬 테스트 토큰(항상 저장 가능)을 써서 "동의 안 함" 경우를 볼 수 없어 따로 둡니다.
class VariantQueryServiceTest {

  private static final Long USER_ID = 7L;
  // 한국 시간 2026-10-24 09:00 → 새벽 4시 기준 날짜도 10월 24일
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final DailyAssignmentService dailyAssignmentService = mock(DailyAssignmentService.class);
  private final AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
  private final VariantRepository variantRepository = mock(VariantRepository.class);
  private final WeeklySetRepository weeklySetRepository = mock(WeeklySetRepository.class);
  private final ReviewItemRepository reviewItemRepository = mock(ReviewItemRepository.class);
  private final SourceProblemRepository sourceProblemRepository =
      mock(SourceProblemRepository.class);
  private final VariantTaxonomy taxonomy = mock(VariantTaxonomy.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC);
  private final VariantQueryService service =
      new VariantQueryService(
          currentUserProvider,
          dailyAssignmentService,
          assignmentRepository,
          variantRepository,
          weeklySetRepository,
          reviewItemRepository,
          sourceProblemRepository,
          taxonomy,
          clock);

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
  }

  @Test
  @DisplayName("동의하지 않은 사람: 오늘 과제를 새로 배정하지 않고, 이미 낸 과제만 보여 준다")
  void noConsentSkipsFill() {
    given(currentUserProvider.canSaveForCurrentUser()).willReturn(false);

    DailyResponse response = service.daily(null);

    verify(dailyAssignmentService, never()).fill(any(), any(), anyBoolean(), any());
    // 저장은 안 해도 조회는 합니다.
    verify(assignmentRepository)
        .findByUserIdAndKindAndAssignedDateOrderByIdAsc(USER_ID, AssignmentKind.DAILY, TODAY);
    assertThat(response.date()).isEqualTo(TODAY);
    assertThat(response.items()).isEmpty();
    assertThat(response.preparingCount()).isZero();
  }

  @Test
  @DisplayName("동의한 사람: 오늘 과제를 배정하고(생성은 안 함), 준비 중인 수를 알려 준다")
  void consentFills() {
    given(currentUserProvider.canSaveForCurrentUser()).willReturn(true);
    given(dailyAssignmentService.fill(USER_ID, TODAY, false, null))
        .willReturn(new DailyAssignmentService.FillResult(0, 2));

    DailyResponse response = service.daily(null);

    verify(dailyAssignmentService).fill(USER_ID, TODAY, false, null);
    assertThat(response.preparingCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("지난 날짜: 동의했어도 새로 배정하지 않는다")
  void pastDateNeverFills() {
    given(currentUserProvider.canSaveForCurrentUser()).willReturn(true);

    DailyResponse response = service.daily(TODAY.minusDays(1));

    verify(dailyAssignmentService, never()).fill(any(), any(), anyBoolean(), any());
    assertThat(response.date()).isEqualTo(TODAY.minusDays(1));
    assertThat(response.preparingCount()).isZero();
  }
}
