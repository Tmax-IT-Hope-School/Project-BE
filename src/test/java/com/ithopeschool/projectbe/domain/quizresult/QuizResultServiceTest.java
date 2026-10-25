package com.ithopeschool.projectbe.domain.quizresult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.quizresult.dto.QuizResultRequest;
import com.ithopeschool.projectbe.domain.quizresult.dto.QuizResultResponse;
import com.ithopeschool.projectbe.domain.quizresult.dto.QuizStatsResponse;
import com.ithopeschool.projectbe.domain.quizresult.dto.QuizStatsResponse.SubjectStats;
import com.ithopeschool.projectbe.domain.quizresult.dto.QuizStatsResponse.UnitStats;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectColor;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 퀴즈 결과 저장의 검사와 기간 통계의 계산을 확인합니다. DB 는 가짜로 바꿉니다. (2026-10-24, 통계 탭)
class QuizResultServiceTest {

  private static final Long ME = 1L;
  private static final Long MATH = 3L;
  // 지금 = 2026-10-24 12:00 (한국 시간) → 오늘은 10-24
  private static final Instant NOW = Instant.parse("2026-10-24T03:00:00Z");
  private static final LocalDate OCT_23 = LocalDate.of(2026, 10, 23);
  private static final LocalDate OCT_24 = LocalDate.of(2026, 10, 24);

  private QuizResultRepository quizResultRepository;
  private SubjectRepository subjectRepository;
  private StudySessionRepository studySessionRepository;
  private QuizResultService service;

  @BeforeEach
  void setUp() {
    CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    quizResultRepository = mock(QuizResultRepository.class);
    subjectRepository = mock(SubjectRepository.class);
    studySessionRepository = mock(StudySessionRepository.class);
    service =
        new QuizResultService(
            currentUserProvider,
            quizResultRepository,
            subjectRepository,
            studySessionRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    given(quizResultRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
  }

  private static Subject math() {
    Subject subject = new Subject(ME, "수학", SubjectColor.BLUE);
    // 과목 번호는 DB 가 정하는 값이라 테스트에서는 직접 넣습니다.
    ReflectionTestUtils.setField(subject, "id", MATH);
    return subject;
  }

  private static QuizResultRequest request(int correct, int total, List<Integer> seconds) {
    return new QuizResultRequest(MATH, " 함수 ", correct, total, seconds);
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
  }

  @Test
  @DisplayName("저장하면 날짜는 오늘(새벽 4시 기준)이고, 단원 앞뒤 빈칸은 지운다")
  void save() {
    given(subjectRepository.findByIdAndUserId(MATH, ME)).willReturn(Optional.of(math()));

    QuizResultResponse response = service.save(request(2, 3, List.of(40, 30, 50)));

    assertThat(response.quizDate()).isEqualTo(OCT_24);
    assertThat(response.unit()).isEqualTo("함수");
    assertThat(response.correctCount()).isEqualTo(2);
    assertThat(response.totalCount()).isEqualTo(3);
    assertThat(response.questionSeconds()).containsExactly(40, 30, 50);
  }

  @Test
  @DisplayName("맞은 개수가 전체보다 많거나, 걸린 시간 개수가 문제 수와 다르면 400 이고 저장하지 않는다")
  void invalidCounts() {
    assertError(
        () -> service.save(request(4, 3, List.of(40, 30, 50))),
        ErrorCode.VALIDATION_ERROR,
        "맞은 개수가 전체 개수보다 많을 수 없어요");
    assertError(
        () -> service.save(request(2, 3, List.of(40, 30))),
        ErrorCode.VALIDATION_ERROR,
        "문제마다 걸린 시간을 문제 수만큼 보내 주세요");
    verify(quizResultRepository, never()).save(any());
  }

  @Test
  @DisplayName("없는 과목이나 남의 과목이면 404 이고 저장하지 않는다")
  void unknownSubject() {
    given(subjectRepository.findByIdAndUserId(MATH, ME)).willReturn(Optional.empty());

    assertError(
        () -> service.save(request(2, 3, List.of(40, 30, 50))),
        ErrorCode.NOT_FOUND,
        "과목을 찾을 수 없어요");
    verify(quizResultRepository, never()).save(any());
  }

  @Test
  @DisplayName("기간이 거꾸로이거나 31일을 넘으면 400")
  void invalidPeriod() {
    assertError(
        () -> service.getStats(OCT_24, OCT_23),
        ErrorCode.VALIDATION_ERROR,
        "시작 날짜는 끝 날짜보다 늦을 수 없어요");
    assertError(
        () -> service.getStats(OCT_24.minusDays(31), OCT_24),
        ErrorCode.VALIDATION_ERROR,
        "한 번에 31일까지만 조회할 수 있어요");
  }

  @Test
  @DisplayName("통계는 과목의 날짜별 합계 · 공부 시간과 단원별 합계 · 평균 풀이 시간을 낸다")
  void stats() {
    given(subjectRepository.findByUserIdOrderById(ME)).willReturn(List.of(math()));
    given(
            quizResultRepository.findByUserIdAndQuizDateBetweenOrderByQuizDateAscIdAsc(
                ME, OCT_23, OCT_24))
        .willReturn(
            List.of(
                new QuizResult(ME, MATH, "함수", OCT_23, 2, 3, List.of(40, 30, 50)),
                new QuizResult(ME, MATH, "도형", OCT_24, 1, 2, List.of(60, 80)),
                new QuizResult(ME, MATH, "함수", OCT_24, 3, 3, List.of(20, 20, 20))));
    // 10-23 에 시작한 수학 공부 30분 + 노는 기록 1시간(빼고 셈) + 10-24 에 시작한 수학 공부 45분
    Instant oct23 = DayRule.startOf(OCT_23).plusSeconds(3600);
    Instant oct24 = DayRule.startOf(OCT_24).plusSeconds(3600);
    given(
            studySessionRepository.findStartedBetween(
                ME, DayRule.startOf(OCT_23), DayRule.startOf(OCT_24.plusDays(1))))
        .willReturn(
            List.of(
                new StudySession(ME, MATH, oct23, oct23.plusSeconds(30 * 60), 0),
                new StudySession(
                    ME,
                    null,
                    oct23,
                    oct23.plusSeconds(60 * 60),
                    0,
                    0,
                    null,
                    null,
                    null,
                    PlanKind.PLAY),
                new StudySession(ME, MATH, oct24, oct24.plusSeconds(45 * 60), 0)));

    QuizStatsResponse stats = service.getStats(OCT_23, OCT_24);

    // 전체 공부 시간: 노는 기록만 빼고 날짜별로 (10-23 = 30분, 10-24 = 45분)
    assertThat(stats.days())
        .extracting(d -> d.date() + " " + d.studyMinutes())
        .containsExactly("2026-10-23 30", "2026-10-24 45");
    assertThat(stats.subjects()).hasSize(1);
    SubjectStats math = stats.subjects().get(0);
    assertThat(math.name()).isEqualTo("수학");
    assertThat(math.color()).isEqualTo("blue");
    // 날짜별: 10-23 = 2/3 · 30분, 10-24 = (1 + 3)/(2 + 3) · 45분
    assertThat(math.days())
        .extracting(
            d -> d.date() + " " + d.correctCount() + "/" + d.totalCount() + " " + d.studyMinutes())
        .containsExactly("2026-10-23 2/3 30", "2026-10-24 4/5 45");
    // 단원별: 처음 나온 순서. 함수 = 5/6, 평균 (40+30+50+20+20+20)/6 = 30초 · 도형 = 1/2, 70초
    UnitStats function = math.units().get(0);
    assertThat(function.unit()).isEqualTo("함수");
    assertThat(function.correctCount()).isEqualTo(5);
    assertThat(function.totalCount()).isEqualTo(6);
    assertThat(function.averageSeconds()).isEqualTo(30);
    assertThat(function.days())
        .extracting(d -> d.correctCount() + "/" + d.totalCount())
        .containsExactly("2/3", "3/3");
    assertThat(math.units().get(1).unit()).isEqualTo("도형");
    assertThat(math.units().get(1).averageSeconds()).isEqualTo(70);
  }
}
