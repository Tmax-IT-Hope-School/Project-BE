package com.ithopeschool.projectbe.domain.summary;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.plan.DayProgress.PlayProgress;
import com.ithopeschool.projectbe.domain.stats.StudyFacts;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 하루 요약표. 한 줄 = 한 사람의 하루치 요약 하나. (이슈 #34, 담당 학생이 설계)
// 매일 밤 예약 작업(DailySummaryJob)이 사용자마다 한 줄씩 만들고, 모든 분석과 리포트가 이 표 하나를 보고 계산합니다.
//
// 같은 사람의 같은 날짜는 한 줄만 있습니다. (uk_daily_summaries_user_id_summary_date)
// 같은 날을 다시 만들면 새 줄을 만들지 않고 그 줄을 덮어씁니다. 그래서 두 번 돌아도 안전합니다.
//
// 값이 없을 때 (담당 학생이 고름)
//   - 타이머 · 계획에서 센 숫자(순공 · 세션 수 · 노는 시간 …)는 0 입니다. 공부를 안 한 날도 순공 0 인 줄이 생깁니다.
//   - 계산할 수 없는 값(계획이 없는 날의 달성률 …)과 학생이 답하지 않은 값(컨디션 · 기분 …)은 비어 있습니다(null, "모름").
//   - 줄이 아예 없는 날 = 아직 요약을 만들지 않은 날(가입 전 포함) / 줄은 있는데 순공 0 = 공부를 안 한 날
//
// 하루에 여러 개인 값은 딸린 테이블에 담습니다. (체크인의 감정과 같은 방식)
//   daily_summary_subjects     : (daily_summary_id, subject_id, study_minutes)  과목마다 한 줄
//   daily_summary_stop_reasons : (daily_summary_id, stop_reason, stop_count)    멈춘 이유마다 한 줄
//   두 테이블의 position 칸은 같은 요약 안에서 줄의 순서(0, 1, 2 …)입니다. JPA 가 줄을 구분하려고 채웁니다.
//
// 컨디션 · 기분 · 미달 원인은 체크인에서 옮겨 온 값이라, 이 표도 로그에 찍지 않습니다. (CLAUDE.md 4장)
@Entity
@Table(
    name = "daily_summaries",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_daily_summaries_user_id_summary_date",
            columnNames = {"user_id", "summary_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySummary {

  // 줄을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 누구의 요약인지(users.id). 다른 사람의 데이터가 섞이지 않게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 어느 날의 요약인지. 하루는 새벽 4시에 바뀝니다. (DayRule, docs/API.md 2-4) (date 는 DB 예약어라 summaryDate)
  @Column(nullable = false)
  private LocalDate summaryDate;

  // 그날의 요일. 1 = 월요일, 2 = 화요일 … 7 = 일요일 (요일별로 묶어 볼 때 씁니다)
  // 팀 규칙(EntityRuleTest)상 엔티티에 java.time 의 DayOfWeek 를 쓸 수 없어서 숫자로 저장합니다. (담당 학생이 고름)
  @Column(nullable = false)
  private int dayOfWeek;

  // ---- 타이머 · 계획에서 계산한 값 (DaySummary) ----

  // 순공시간(분). 일시정지한 시간은 빼고, 노는 기록은 넣지 않습니다. 공부를 안 했으면 0
  @Column(nullable = false)
  private int studyMinutes;

  // 계획 대비 달성률(0 ~ 100). 공부 계획이 없는 날은 비어 있습니다. (AchievementRate)
  private Integer achievementRate;

  // 그날 가장 먼저 공부를 시작한 시각. 공부 기록이 없으면 비어 있습니다.
  private Instant firstStartAt;

  // 공부 기록(세션) 수. 노는 기록은 세지 않습니다.
  @Column(nullable = false)
  private int sessionCount;

  // 기록 하나의 평균 순공시간(분). 공부 기록이 없으면 0
  @Column(nullable = false)
  private int averageSessionMinutes;

  // 일시정지를 누른 횟수의 합계
  @Column(nullable = false)
  private int pauseCount;

  // ---- 학생이 답한 값 (체크인 · 하루 신호). 답하지 않았으면 비어 있습니다 ----

  // 그날 컨디션 (1 ~ 5)
  private Integer condition;

  // 그날 기분 (1 ~ 5, 1 이 가장 처짐)
  private Integer mood;

  // 그날 계획을 못 채운 이유. "다음 날" 체크인에서 고릅니다. (체크인의 미달 원인 = 어제 못 채운 이유)
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private MissReason missReason;

  // 예상 순공시간(분). 그날 체크인에 적은 값이 있으면 그 값, 없으면 그날 공부 계획 목표 시간의 합입니다.
  @Column(nullable = false)
  private int expectedMinutes;

  // 개선안을 실천했는지 (예 / 아니오)
  private Boolean suggestionKept;

  // 잠잔 시간(분). 하루 신호(daily_signals)에 있으면 옮겨 옵니다.
  private Integer sleepMinutes;

  // ---- 타이머의 목표 · 완주 (StudyFacts) ----

  // 목표 시간을 정하고 켠 공부 타이머 수
  @Column(nullable = false)
  private int targetSessionCount;

  // 그중 목표 시간을 다 채운(완주한) 타이머 수
  @Column(nullable = false)
  private int completedSessionCount;

  // 계획한 시작 시각보다 평균 몇 분 늦게 시작했는지. 시작 시각이 있는 계획을 하나도 시작하지 않았으면 비어 있습니다.
  private Integer averageStartDelayMinutes;

  // 목표를 못 채우고 멈춘 타이머가 평균 몇 %에서 멈췄는지. 멈춘 타이머가 없으면 비어 있습니다.
  private Integer stopProgressPercent;

  // ---- 노는 계획 (DayProgress.playOf) ----

  // 그날 놀기로 계획한 시간의 합(분)
  @Column(nullable = false)
  private int playPlannedMinutes;

  // 노는 계획으로 실제 논 시간의 합(분)
  @Column(nullable = false)
  private int playActualMinutes;

  // ---- 하루에 여러 개인 값 (딸린 테이블) ----

  // 과목별 순공. 분을 모두 더하면 studyMinutes 와 같습니다.
  @ElementCollection
  @CollectionTable(
      name = "daily_summary_subjects",
      joinColumns = @JoinColumn(name = "daily_summary_id"))
  @OrderColumn(name = "position")
  private List<DailySummarySubject> subjects = new ArrayList<>();

  // 멈춘 이유와 횟수
  @ElementCollection
  @CollectionTable(
      name = "daily_summary_stop_reasons",
      joinColumns = @JoinColumn(name = "daily_summary_id"))
  @OrderColumn(name = "position")
  private List<DailySummaryStopReason> stopReasons = new ArrayList<>();

  // 학생이 답한 값을 한데 모은 것입니다. 답하지 않은 칸은 null 입니다.
  // condition · mood · suggestionKept: 그날 체크인 / missReason: 다음 날 체크인 / sleepMinutes: 그날 하루 신호
  // expectedMinutes: 그날 체크인의 값, 없으면 그날 공부 계획 목표 시간의 합 (null 이 아님)
  public record Answers(
      Integer condition,
      Integer mood,
      MissReason missReason,
      int expectedMinutes,
      Boolean suggestionKept,
      Integer sleepMinutes) {}

  // 빈 요약 줄을 만듭니다. 값은 바로 overwrite 로 채웁니다.
  public DailySummary(Long userId, LocalDate summaryDate) {
    this.userId = userId;
    this.summaryDate = summaryDate;
    // 자바의 요일 번호를 그대로 씁니다. (MONDAY.getValue() = 1 … SUNDAY.getValue() = 7)
    this.dayOfWeek = summaryDate.getDayOfWeek().getValue();
  }

  // 새로 계산한 값으로 전부 바꿉니다. 예전 값은 남기지 않습니다. (같은 날을 다시 만들면 덮어쓰기)
  // sessionCount 는 DaySummary 에 없어서(FE 의 getDaySummary 와 모양을 맞춰 둔 값이라 고치지 않음) 따로 받습니다.
  public void overwrite(
      DaySummary day,
      int sessionCount,
      StudyFacts facts,
      PlayProgress play,
      Answers answers,
      List<DailySummarySubject> subjects,
      List<DailySummaryStopReason> stopReasons) {
    this.studyMinutes = day.studyMinutes();
    this.achievementRate = day.achievementRate();
    this.firstStartAt = day.firstStartAt();
    this.sessionCount = sessionCount;
    this.averageSessionMinutes = day.averageSessionMinutes();
    this.pauseCount = day.pauseCount();

    this.condition = answers.condition();
    this.mood = answers.mood();
    this.missReason = answers.missReason();
    this.expectedMinutes = answers.expectedMinutes();
    this.suggestionKept = answers.suggestionKept();
    this.sleepMinutes = answers.sleepMinutes();

    // "목표가 있는 타이머 수" 는 완주율을 계산한 표본 수와 같습니다. (StudyFacts 규칙 5, 6)
    this.targetSessionCount = facts.completionRate().sampleCount();
    this.completedSessionCount = facts.completedCount();
    this.averageStartDelayMinutes = facts.startDelayMinutes().value();
    this.stopProgressPercent = facts.stopPoint().value();

    this.playPlannedMinutes = play.targetMinutes();
    this.playActualMinutes = play.actualMinutes();

    // 목록은 새 객체로 바꾸지 않고 비운 뒤 채웁니다. (JPA 가 지켜보는 목록이라서)
    this.subjects.clear();
    this.subjects.addAll(subjects);
    this.stopReasons.clear();
    this.stopReasons.addAll(stopReasons);
  }
}
