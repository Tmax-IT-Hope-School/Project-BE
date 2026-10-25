package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// AI 하루 계획의 숫자 · 시각 규칙을 하나씩 확인합니다. 시각은 "하루 시작(새벽 4시) 기준 분" 입니다.
class DayPlanRuleTest {

  private static int at(int hour, int minute) {
    return DayPlanRule.dayMinutesOf(LocalTime.of(hour, minute));
  }

  private static DayPlanRule.Span span(int fromHour, int fromMinute, int toHour, int toMinute) {
    return new DayPlanRule.Span(at(fromHour, fromMinute), at(toHour, toMinute));
  }

  // ---- 하루 총량 ----

  @Test
  @DisplayName("공부한 날이 3일 이상이면 평균의 1.1배를 10분 단위로, 이미 있는 카드 시간을 뺀다")
  void totalFromRecords() {
    DayPlanRule.Total total = DayPlanRule.totalMinutesOf(List.of(100, 120, 140), null, false, 30);

    assertThat(total.minutes()).isEqualTo(100); // 120 × 1.1 = 132 → 130, − 30
    assertThat(total.evidence())
        .containsExactly("최근 14일 공부한 3일의 평균이 120분이라 조금 더 잡았어요", "그날 이미 있는 카드 30분을 뺐어요");
  }

  @Test
  @DisplayName("기록이 적으면 설문 → 120분, 계획 낙관이 높으면 0.8배, 30 ~ 600분 안으로")
  void totalFallbacks() {
    assertThat(DayPlanRule.totalMinutesOf(List.of(50), 180, false, 0).minutes()).isEqualTo(180);
    assertThat(DayPlanRule.totalMinutesOf(List.of(), null, true, 0).minutes()).isEqualTo(100);
    assertThat(DayPlanRule.totalMinutesOf(List.of(10, 10, 10), null, false, 0).minutes())
        .isEqualTo(30);
    assertThat(DayPlanRule.totalMinutesOf(List.of(), 60, false, 90).minutes()).isZero();
  }

  @Test
  @DisplayName("계획 방에서 학생이 정한 총량은 그대로 쓰고(30분보다 적으면 30분), 이미 있는 카드 시간만 뺀다 (#431)")
  void chosenTotal() {
    DayPlanRule.Total total = DayPlanRule.chosenTotalOf(120, 0);
    assertThat(total.minutes()).isEqualTo(120);
    assertThat(total.basis()).isEqualTo("계획 방에서 정한 대로");
    assertThat(total.evidence()).containsExactly("계획 방에서 오늘 120분 하기로 해서 그만큼 잡았어요");

    // 기록 평균 · 계획 낙관으로 바꾸지 않습니다. 75분처럼 10분 단위가 아니어도 그대로입니다.
    assertThat(DayPlanRule.chosenTotalOf(75, 0).minutes()).isEqualTo(75);
    // 한 칸은 30분 이상이라 20분은 30분으로
    DayPlanRule.Total small = DayPlanRule.chosenTotalOf(20, 0);
    assertThat(small.minutes()).isEqualTo(30);
    assertThat(small.evidence()).contains("한 칸은 30분 이상이라 30분으로 잡았어요");
    // 오늘 이미 있는 카드까지 합친 총량이라 카드 시간을 뺍니다.
    DayPlanRule.Total withCards = DayPlanRule.chosenTotalOf(120, 60);
    assertThat(withCards.minutes()).isEqualTo(60);
    assertThat(withCards.evidence()).contains("그날 이미 있는 카드 60분을 뺐어요");
    assertThat(DayPlanRule.chosenTotalOf(90, 120).minutes()).isZero();
  }

  @Test
  @DisplayName("학생이 정한 시작 시각 전은 빼고, 이미 그보다 늦으면 그대로, 끝보다 늦으면 빈 시간이 없다 (#431)")
  void startingAt() {
    DayPlanRule.Span window = span(8, 0, 23, 0);

    assertThat(DayPlanRule.startingAt(window, LocalTime.of(21, 0))).isEqualTo(span(21, 0, 23, 0));
    assertThat(DayPlanRule.startingAt(window, LocalTime.of(7, 0))).isEqualTo(window);
    DayPlanRule.Span late = DayPlanRule.startingAt(window, LocalTime.of(23, 30));
    assertThat(late).isEqualTo(span(23, 30, 23, 30));
    assertThat(DayPlanRule.freeSlotsOf(late, List.of())).isEmpty();
  }

  // ---- 시각 ----

  @Test
  @DisplayName("시각은 하루 시작 기준 분으로 바꾸고, 다시 HH:mm 으로 쓴다")
  void timeConversion() {
    assertThat(at(4, 0)).isEqualTo(240);
    assertThat(at(0, 0)).isEqualTo(1440);
    assertThat(DayPlanRule.timeTextOf(1140)).isEqualTo("19:00");
    assertThat(DayPlanRule.timeTextOf(1500)).isEqualTo("01:00");
  }

  @Test
  @DisplayName("공부할 수 있는 시간은 수면 보호(최소 6시간)와 일어난 뒤 1시간을 뺀 나머지 (FE getSleepWindow 와 같은 예시)")
  void studyWindow() {
    assertThat(DayPlanRule.studyWindowOf(LocalTime.of(23, 0), LocalTime.of(7, 0)))
        .isEqualTo(span(8, 0, 23, 0));
    // 4시간만 자면 6시간으로 늘려서 07:00 에 일어난 것으로 보고, 1시간 뒤부터
    assertThat(DayPlanRule.studyWindowOf(LocalTime.of(1, 0), LocalTime.of(5, 0)))
        .isEqualTo(span(8, 0, 1, 0));
    // 모르면 00:00 · 06:00
    assertThat(DayPlanRule.studyWindowOf(null, null)).isEqualTo(span(7, 0, 0, 0));
  }

  @Test
  @DisplayName("빈 시간은 고정 일정 · 카드 앞뒤 10분을 빼고, 30분보다 짧은 틈은 버린다")
  void freeSlots() {
    List<DayPlanRule.Span> free =
        DayPlanRule.freeSlotsOf(
            span(7, 0, 23, 0),
            List.of(span(19, 0, 20, 0), span(8, 0, 16, 30), span(20, 15, 21, 0)));

    // 07:00 ~ 07:50 · 16:40 ~ 18:50 · (20:10 ~ 20:05 는 없음) · 21:10 ~ 23:00
    assertThat(free).containsExactly(span(7, 0, 7, 50), span(16, 40, 18, 50), span(21, 10, 23, 0));
  }

  @Test
  @DisplayName("칸은 빈 시간 앞에서부터 한 칸 길이로, 칸 사이 10분을 띄워 놓는다")
  void placeEarlyFirst() {
    List<DayPlanRule.Placed> placed =
        DayPlanRule.placeBlocks(List.of(span(16, 40, 23, 0)), 50, 140, false);

    assertThat(placed)
        .containsExactly(
            new DayPlanRule.Placed(at(16, 40), 50),
            new DayPlanRule.Placed(at(17, 40), 50),
            new DayPlanRule.Placed(at(18, 40), 40));
  }

  @Test
  @DisplayName("남은 총량이 30분보다 짧으면 칸을 더 놓지 않는다 (10 · 20분짜리 칸은 만들지 않음)")
  void noBlockShorterThanStudyBlock() {
    List<DayPlanRule.Placed> placed =
        DayPlanRule.placeBlocks(List.of(span(16, 40, 23, 0)), 50, 120, false);

    assertThat(placed)
        .containsExactly(
            new DayPlanRule.Placed(at(16, 40), 50), new DayPlanRule.Placed(at(17, 40), 50));
    assertThat(placed).allSatisfy(block -> assertThat(block.minutes()).isGreaterThanOrEqualTo(30));
  }

  @Test
  @DisplayName("저녁형은 늦은 빈 시간의 끝에서부터 채우고, 결과는 시각 순이다")
  void placeLateFirst() {
    List<DayPlanRule.Placed> placed =
        DayPlanRule.placeBlocks(List.of(span(7, 0, 7, 50), span(16, 40, 23, 0)), 50, 120, true);

    assertThat(placed)
        .containsExactly(
            new DayPlanRule.Placed(at(21, 10), 50), new DayPlanRule.Placed(at(22, 10), 50));
  }

  @Test
  @DisplayName("빈 시간이 짧으면 그만큼만, 30분보다 짧으면 놓지 않는다")
  void placeLimitedByFreeTime() {
    List<DayPlanRule.Placed> placed =
        DayPlanRule.placeBlocks(List.of(span(7, 0, 7, 40), span(21, 0, 21, 25)), 50, 120, false);

    assertThat(placed).containsExactly(new DayPlanRule.Placed(at(7, 0), 40));
  }

  // ---- 목표 ----

  @Test
  @DisplayName("D-day 가 가까운 목표부터, 14일 안인 목표는 한 바퀴에 두 번")
  void goalRotation() {
    DayPlanRule.GoalInfo math = new DayPlanRule.GoalInfo("4", "수학", 5, null);
    DayPlanRule.GoalInfo english = new DayPlanRule.GoalInfo("5", "영어", 30, "30쪽");

    assertThat(DayPlanRule.goalRotation(List.of(english, math), 4))
        .containsExactly(math, english, math, math);
    assertThat(DayPlanRule.goalRotation(List.of(), 3)).isEmpty();
  }

  @Test
  @DisplayName("제목에 든 과목 이름을 찾는다. 긴 이름부터 봐서 '수학Ⅱ' 를 '수학' 보다 먼저")
  void subjectInTitle() {
    assertThat(DayPlanRule.subjectInTitle("수학 오답", List.of("국어", "영어", "수학"))).isEqualTo("수학");
    assertThat(DayPlanRule.subjectInTitle("수학Ⅱ 미분", List.of("수학", "수학Ⅱ"))).isEqualTo("수학Ⅱ");
    assertThat(DayPlanRule.subjectInTitle("기말 전 영단어장", List.of("국어", "영어", "수학"))).isNull();
    assertThat(DayPlanRule.subjectInTitle(null, List.of("수학"))).isNull();
  }

  @Test
  @DisplayName("먼저 할 것이 있으면 이름이 겹치는 목표를 첫 칸으로 옮기고, 없으면 첫 칸은 목표 없이 둔다")
  void withFirstTask() {
    DayPlanRule.GoalInfo words = new DayPlanRule.GoalInfo("4", "기말 전 영단어장 1회독", 13, "22쪽");
    DayPlanRule.GoalInfo math = new DayPlanRule.GoalInfo("5", "11월 학평 수학 3등급", 33, null);

    assertThat(DayPlanRule.withFirstTask(List.of(words, math), "수학 오답"))
        .containsExactly(math, words);
    assertThat(DayPlanRule.withFirstTask(List.of(words, words), "국어 비문학"))
        .containsExactly(null, words);
    assertThat(DayPlanRule.withFirstTask(List.of(words, math), null)).containsExactly(words, math);
    assertThat(DayPlanRule.withFirstTask(List.of(), "수학 오답")).isEmpty();
  }

  @Test
  @DisplayName("완주형 하루 분량은 남은 분량 ÷ 남은 날(그날 포함) 올림 (FE getDailyPace 와 같은 예시)")
  void dailyPace() {
    assertThat(DayPlanRule.dailyPaceOf(300, 9)).isEqualTo(30);
    assertThat(DayPlanRule.dailyPaceOf(100, 0)).isEqualTo(100);
    assertThat(DayPlanRule.dailyPaceOf(100, 2)).isEqualTo(34);
  }
}
