package com.ithopeschool.projectbe.domain.card;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 카드 테이블. 한 줄 = 카드 한 장 = "언제 + 무엇을" 정한 계획 하나. (최종 기획서 「4.3 카드」, 이슈 #222, FE 의 Card)
// 카드는 1회성 카드와 요일마다 반복하는 카드가 있습니다.
//   - 1회성 카드: cardDate 그날만 합니다. repeatDays 가 비어 있습니다.
//   - 반복 카드: cardDate(반복을 시작한 날)부터, repeatDays 의 요일마다 합니다. 날마다 복사하지 않고 한 장으로 둡니다.
// 그날 했는지는 카드가 아니라 세션 기록(이슈 #224)에 남깁니다.
//
// study_plans 에 칸을 더하지 않고 새 테이블로 만든 이유 (#222 댓글):
//   ① 화면 이름(카드 탭)과 주소(/api/v1/cards)를 맞춤 ② 반복 카드는 한 장이라 날짜마다 한 줄인 study_plans 와 모양이 다름
//   ③ 옛 FE 코드가 쓰는 /study-plans 를 깨지 않음
//
// 한 카드에 여러 개인 값은 딸린 테이블(@ElementCollection)에 담습니다. (ThinkingTest 와 같은 방식)
//   card_checklist_items      : (card_id, position, item_key, text)  체크리스트 항목마다 한 줄
//   card_expected_fail_causes : (card_id, position, cause)           예상 실패 원인마다 한 줄
// position 은 보낸 순서(0, 1, 2 ...)입니다. 화면에 보낸 순서 그대로 보여 주려고 적어 둡니다. (@OrderColumn)
//
// "그날의 내 카드" 를 찾는 일이 가장 많아서 (user_id, card_date) 에 색인(찾기 쉽게 만든 목차)을 만들었습니다.
@Entity
@Table(
    name = "cards",
    indexes = @Index(name = "idx_cards_user_id_card_date", columnList = "user_id, card_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Card {

  // 카드를 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 카드의 주인(users.id). 다른 사람의 카드가 섞이지 않게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 무엇을 하는지 (예: "수학 문제집"). 100자까지
  @Column(nullable = false, length = 100)
  private String title;

  // 1회성 카드면 그 날짜, 반복 카드면 반복을 시작한 날짜. (FE 의 date. date 는 DB 예약어라 cardDate 로 지었습니다)
  @Column(nullable = false)
  private LocalDate cardDate;

  // 반복 요일을 쉼표로 이은 글자. 0 = 일요일, 1 = 월요일 … 6 = 토요일 (FE 의 Date.getDay() 와 같은 숫자)
  // 예: 월·수·금 → "1,3,5". 작은 숫자부터 적고, 1회성 카드면 비어 있습니다(null).
  // 요일은 7개뿐이라 딸린 테이블로 빼지 않고 한 칸에 적었습니다. 숫자 목록으로 꺼낼 때는 repeatDayList() 를 씁니다.
  @Column(length = 20)
  private String repeatDays;

  // 시작 시각. 예: 19:00 (몇 시 몇 분만). 새 카드는 꼭 있고, 옛 계획에서 옮긴 카드만 비어 있을 수 있습니다.
  private LocalTime startTime;

  // 길이(분). 1 ~ 600
  @Column(nullable = false)
  private int durationMinutes;

  // 체크리스트 (1 ~ 20개). 체크했는지는 세션 기록에 남깁니다. (ChecklistItem 설명)
  @ElementCollection
  @CollectionTable(name = "card_checklist_items", joinColumns = @JoinColumn(name = "card_id"))
  @OrderColumn(name = "position")
  private List<ChecklistItem> checklist = new ArrayList<>();

  // 연결한 목표의 번호. 없으면 "목표 없음" 입니다.
  // ⚠️ 목표 테이블(goals, 이슈 #221)이 아직 없어서 지금은 번호만 적고, 그 목표가 있는지는 검사하지 않습니다.
  private Long goalId;

  // 어떤 과목의 카드인지(subjects.id). 없으면 "과목 없음" 입니다. (이슈 #404)
  // 카드로 켠 공부 기록에 과목을 안 보내면 이 과목을 채웁니다. 그래야 과목별 공부 시간이 '기타' 로 묶이지 않습니다.
  // goalId 처럼 번호만 적고 DB 외래 키는 걸지 않았습니다. 과목을 지우면 SubjectService 가 이 칸을 비웁니다.
  private Long subjectId;

  // 우선순위. 절대(ABSOLUTE) / 유연(FLEXIBLE, 기본)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private CardPriority priority;

  // 유형. 자습(SELF, 기본) / 인강(LECTURE)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private CardType type;

  // 예상 달성 개수 (0 ~ 체크리스트 개수). 비어 있으면 "체크리스트 전부" 로 봅니다.
  private Integer expectedDone;

  // 예상 달성 개수를 낮춘 이유 (태그 하나, 30자까지). 낮추지 않았으면 비어 있습니다.
  @Column(length = 30)
  private String expectedDoneReason;

  // 예상 실패 원인 (5개까지, 하나에 100자까지). 처음엔 주관식이라 글자로 적습니다.
  @ElementCollection
  @CollectionTable(name = "card_expected_fail_causes", joinColumns = @JoinColumn(name = "card_id"))
  @OrderColumn(name = "position")
  @Column(name = "cause", nullable = false, length = 100)
  private List<String> expectedFailCauses = new ArrayList<>();

  // 알림 방법. 메시지(MESSAGE) / 전화(CALL). 알림 세 칸이 모두 비어 있으면 기본(정각 · 메시지 · 기본 문구)입니다.
  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private AlertMethod alertMethod;

  // 시작 몇 분 전에 알릴지 (0 ~ 120). 0 이면 정각
  private Integer alertMinutesBefore;

  // 알림에 보일 문장 (100자까지). 비어 있으면 기본 문구
  @Column(length = 100)
  private String alertText;

  // 카드 편집 화면을 연 순간부터 저장할 때까지 걸린 시간(밀리초). '계획 대 실행 비율' 에 씁니다.
  private Long planningMs;

  // 카드를 만든 시각 (서버에 저장한 시각)
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  // 꼭 있어야 하는 값으로 카드를 만듭니다. 우선순위는 유연, 유형은 자습으로 시작합니다.
  // 나머지(반복 요일·시작 시각·목표·알림 등)는 Service 가 아래 change 메서드로 채웁니다.
  public Card(
      Long userId,
      String title,
      LocalDate cardDate,
      int durationMinutes,
      List<ChecklistItem> checklist,
      Instant createdAt) {
    this.userId = userId;
    this.title = title;
    this.cardDate = cardDate;
    this.durationMinutes = durationMinutes;
    this.checklist = new ArrayList<>(checklist);
    this.priority = CardPriority.FLEXIBLE;
    this.type = CardType.SELF;
    this.createdAt = createdAt;
  }

  // ---- 읽기 도우미 ----

  // 반복 요일을 숫자 목록으로 돌려줍니다. 1회성 카드면 빈 목록입니다.
  //   "1,3,5" → [1, 3, 5]      null → []
  public List<Integer> repeatDayList() {
    List<Integer> days = new ArrayList<>();
    if (repeatDays == null || repeatDays.isEmpty()) {
      return days;
    }
    for (String day : repeatDays.split(",")) {
      days.add(Integer.parseInt(day));
    }
    return days;
  }

  // 1회성 카드인지 (반복 요일이 없으면 1회성)
  public boolean isOneOff() {
    return repeatDayList().isEmpty();
  }

  // 그 날짜에 하는 카드인지 알려 줍니다. FE 의 isCardOnDate (src/lib/cards.ts) 와 같은 규칙입니다.
  //   - 1회성 카드: 카드 날짜와 같은 날만
  //   - 반복 카드: 반복을 시작한 날짜부터, 그 요일이면
  //
  //   (2026-10-12 는 월요일)
  //   1회성 10-12 카드          → 10-12 true,  10-13 false
  //   반복 10-01 시작 [월, 수]   → 10-12 true (월요일),  10-13 false (화요일)
  //   반복 10-20 시작 [월, 수]   → 10-12 false (시작 전)
  public boolean isOnDate(LocalDate date) {
    List<Integer> days = repeatDayList();
    if (days.isEmpty()) {
      return cardDate.equals(date);
    }
    return !date.isBefore(cardDate) && days.contains(weekdayOf(date));
  }

  // 날짜의 요일을 FE 와 같은 숫자로 바꿉니다. (0 = 일요일 … 6 = 토요일)
  // 자바의 요일 번호는 월요일 1 … 일요일 7 이라서, 7 로 나눈 나머지를 쓰면 일요일만 0 이 되고 나머지는 그대로입니다.
  public static int weekdayOf(LocalDate date) {
    return date.getDayOfWeek().getValue() % 7;
  }

  // 그 날짜의 시작 시각을 한 순간(Instant)으로 바꿉니다. FE 의 getPlannedStartAt (src/lib/facts.ts) 와 같은 규칙입니다.
  // 하루는 새벽 4시에 시작하므로(DayRule), 00:00 ~ 03:59 는 달력으로 다음 날 새벽입니다.
  //   (10-12, 19:00) → 2026-10-12 19:00 (한국 시간)
  //   (10-12, 01:30) → 2026-10-13 01:30 (한국 시간)  — 10-12 의 밤샘 카드
  public static Instant startAtOf(LocalDate date, LocalTime startTime) {
    LocalDate calendarDay = startTime.getHour() < 4 ? date.plusDays(1) : date;
    return calendarDay.atTime(startTime).atZone(ZoneId.of("Asia/Seoul")).toInstant();
  }

  // 반복 요일 목록을 DB 에 적을 글자로 바꿉니다. 비어 있으면 null(1회성)입니다.
  // 요일이 겹치지 않는지, 0 ~ 6 인지는 Service 가 먼저 검사합니다. 여기서는 작은 숫자부터 줄 세워 잇기만 합니다.
  //   [5, 1, 3] → "1,3,5"      [] → null
  public static String toRepeatDaysText(List<Integer> days) {
    if (days == null || days.isEmpty()) {
      return null;
    }
    List<Integer> sorted = new ArrayList<>(days);
    sorted.sort(null);
    StringBuilder text = new StringBuilder();
    for (Integer day : sorted) {
      if (!text.isEmpty()) {
        text.append(',');
      }
      text.append(day);
    }
    return text.toString();
  }

  // ---- 바꾸기 (Service 가 검사를 마친 값만 넘깁니다) ----

  // 제목을 바꿉니다.
  public void changeTitle(String title) {
    this.title = title;
  }

  // 날짜(1회성 날짜 / 반복 시작일)를 바꿉니다.
  public void changeCardDate(LocalDate cardDate) {
    this.cardDate = cardDate;
  }

  // 반복 요일을 바꿉니다. 빈 목록이면 1회성 카드가 됩니다.
  public void changeRepeatDays(List<Integer> days) {
    this.repeatDays = toRepeatDaysText(days);
  }

  // 시작 시각을 바꿉니다. null 이면 시각이 없는 카드가 됩니다.
  public void changeStartTime(LocalTime startTime) {
    this.startTime = startTime;
  }

  // 길이(분)를 바꿉니다.
  public void changeDurationMinutes(int durationMinutes) {
    this.durationMinutes = durationMinutes;
  }

  // 체크리스트를 통째로 바꿉니다. (항목 하나만 고치는 방법은 없습니다. 바뀐 목록 전체를 받습니다)
  // 새 목록으로 갈아 끼우지 않고 원래 목록을 비우고 채우는 이유: JPA 가 지켜보는 목록이 바뀌어야 DB 에 반영되기 때문입니다.
  public void changeChecklist(List<ChecklistItem> checklist) {
    this.checklist.clear();
    this.checklist.addAll(checklist);
  }

  // 연결한 목표를 바꿉니다. null 이면 "목표 없음" 입니다.
  public void changeGoalId(Long goalId) {
    this.goalId = goalId;
  }

  // 과목을 바꿉니다. null 이면 "과목 없음" 입니다. (내 과목인지는 Service 가 먼저 검사합니다)
  public void changeSubjectId(Long subjectId) {
    this.subjectId = subjectId;
  }

  // 우선순위를 바꿉니다.
  public void changePriority(CardPriority priority) {
    this.priority = priority;
  }

  // 유형을 바꿉니다.
  public void changeType(CardType type) {
    this.type = type;
  }

  // 예상 달성 개수를 바꿉니다.
  public void changeExpectedDone(Integer expectedDone) {
    this.expectedDone = expectedDone;
  }

  // 예상 달성 개수를 낮춘 이유를 바꿉니다. null 이면 지웁니다.
  public void changeExpectedDoneReason(String expectedDoneReason) {
    this.expectedDoneReason = expectedDoneReason;
  }

  // 예상 실패 원인을 통째로 바꿉니다. 빈 목록이면 지웁니다.
  public void changeExpectedFailCauses(List<String> causes) {
    this.expectedFailCauses.clear();
    this.expectedFailCauses.addAll(causes);
  }

  // 알림 세 칸을 함께 바꿉니다. 셋이 같이 맞아야 해서(방법 없이 문구만 있으면 안 됨) 한 번에 바꿉니다.
  public void changeAlert(AlertMethod method, Integer minutesBefore, String text) {
    this.alertMethod = method;
    this.alertMinutesBefore = minutesBefore;
    this.alertText = text;
  }

  // 계획에 쓴 시간(밀리초)을 바꿉니다.
  public void changePlanningMs(Long planningMs) {
    this.planningMs = planningMs;
  }
}
