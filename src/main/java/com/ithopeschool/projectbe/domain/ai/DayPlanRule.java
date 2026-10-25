package com.ithopeschool.projectbe.domain.ai;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// AI 하루 계획(POST /ai/day-plan)에서 "숫자 · 시각" 을 정하는 규칙입니다. 모두 내 기록으로 코드가 정하고, AI 는 문장만 씁니다.
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다. 시각은 모두 "하루 시작(새벽 4시)부터 센 분" 입니다. (FE 의 toDayMinutes)
//   04:00 → 240,  19:00 → 1140,  00:00 → 1440 (다음 날 새벽이라 하루의 끝쪽)
//
// ■ 하루 총량 (totalMinutesOf)
//   1. 최근 14일 중 공부한 날이 3일 이상이면 그날들 순공 평균의 1.1배 (조금씩 늘리기)
//   2. 아니면 처음 설문의 "하루 공부 가능 시간", 그것도 없으면 120분
//   3. 심리검사의 계획 낙관이 높으면 0.8배로 줄입니다. (계획을 크게 잡는 사람을 위해)
//   4. 10분 단위로 반올림하고 30 ~ 600분 안으로 맞춘 뒤, 그날 이미 있는 카드 시간을 뺍니다.
//   학생이 계획 방에서 총량을 직접 정했으면(요청의 totalMinutes) 1 ~ 3 대신 그 값을 씁니다. (chosenTotalOf, 2026-10-25 #431)
//
// ■ 공부할 수 있는 시간 (studyWindowOf) — 수면 보호 시간(최소 6시간, FE 의 getSleepWindow)과 일어난 뒤 1시간을 뺀 나머지
//   학생이 시작 시각을 정했으면(요청의 startTime) 그 전은 뺍니다. (startingAt, #431)
// ■ 빈 시간 (freeSlotsOf) — 고정 일정(학교 · 학원)과 그날 카드의 앞뒤로 10분씩 여유를 두고 뺀 나머지
//   학교를 등록했는데 그날(평일) 수업 시간과 겹치는 고정 일정이 없으면, 기본 수업 시간(classHours)도 뺍니다.
// ■ 칸 놓기 (placeBlocks) — 빈 시간에 한 칸 길이로 채우고, 칸 사이도 10분 띄웁니다. 저녁형은 늦은 시간부터 채웁니다.
//   한 칸은 30분 이상입니다(StudyBlock). 남은 시간이 30분보다 짧으면 칸을 더 놓지 않습니다. (총량은 칸 수로 맞춤)
// ■ 목표 순서 (goalRotation) — D-day 가 가까운 목표부터, 14일 안이면 한 바퀴에 두 번
public final class DayPlanRule {

  static final int MIN_STUDY_DAYS = 3;
  static final int MIN_TOTAL = 30;
  static final int MAX_TOTAL = 600;
  static final int DEFAULT_TOTAL = 120;
  static final double GROW = 1.1;
  static final double SHRINK = 0.8;
  // 칸 하나의 가장 짧은 길이 (이보다 짧은 빈 시간에는 칸을 놓지 않음). 30분 밑은 공부 블록으로 보지 않음 (StudyBlock)
  static final int MIN_BLOCK = StudyBlock.MIN_MINUTES;
  // 일정 사이 여유 (FE 의 BUFFER_MINUTES)
  static final int BUFFER = 10;
  // 수면 보호는 최소 6시간 (FE 의 SLEEP_PROTECT_MIN_MINUTES)
  static final int SLEEP_PROTECT_MIN = 6 * 60;
  // 일어나서 이만큼은 비웁니다 (씻기 · 아침 · 등교 준비)
  static final int AFTER_WAKE = 60;
  // 취침 · 기상을 모를 때 (FE 와 같음): 00:00 에 자고 06:00 에 일어남
  static final LocalTime DEFAULT_SLEEP = LocalTime.MIDNIGHT;
  static final LocalTime DEFAULT_WAKE = LocalTime.of(6, 0);
  // D-day 가 이 날 수 안이면 "곧" 으로 봅니다
  static final int SOON_DAYS = 14;
  // 한 번에 만드는 칸의 최대 수
  static final int MAX_BLOCKS = 12;
  // 학교를 등록했는데 수업 시간을 고정 일정에 안 넣은 학생의 평일 기본 수업 시간 (고정 일정 주석의 학교 예시와 같음)
  static final LocalTime CLASS_START = LocalTime.of(8, 0);
  static final LocalTime CLASS_END = LocalTime.of(16, 30);

  static final int DAY_START = 4 * 60;
  static final int DAY_END = DAY_START + 24 * 60;

  // 하루 총량(이미 있는 카드를 뺀 것)과 그 근거 문장들
  // basis: 총량을 어디서 정했는지 짧게 ("최근 평균으로" · "처음 설문대로" · "기본으로"). 근거 첫 줄에 씁니다.
  public record Total(int minutes, String basis, List<String> evidence) {}

  // 하루 안의 시간 하나 [start, end) — 하루 시작 기준 분
  public record Span(int start, int end) {
    int length() {
      return end - start;
    }
  }

  // 놓은 칸 하나: 시작(하루 시작 기준 분)과 길이
  public record Placed(int start, int minutes) {}

  // 목표 하나의 재료. daysLeft: 그날부터 D-day 까지 남은 날 / pace: 완주형의 하루 분량 글 ("30쪽"), 점수형이면 null
  public record GoalInfo(String id, String title, int daysLeft, String pace) {}

  private DayPlanRule() {}

  // 하루 총량과 근거
  //   dailyMinutes: 최근 14일 중 공부한 날마다의 순공(분) / selfDaily: 처음 설문의 하루 공부 가능 시간(없으면 null)
  //   planOptimismHigh: 심리검사의 계획 낙관이 높은지 / alreadyPlanned: 그날 이미 있는 카드 시간(분)
  public static Total totalMinutesOf(
      List<Integer> dailyMinutes, Integer selfDaily, boolean planOptimismHigh, int alreadyPlanned) {
    List<String> evidence = new ArrayList<>();
    double total;
    String basis;
    if (dailyMinutes.size() >= MIN_STUDY_DAYS) {
      int sum = 0;
      for (int minutes : dailyMinutes) {
        sum += minutes;
      }
      int average = (int) Math.round((double) sum / dailyMinutes.size());
      total = average * GROW;
      basis = "최근 평균으로";
      evidence.add("최근 14일 공부한 " + dailyMinutes.size() + "일의 평균이 " + average + "분이라 조금 더 잡았어요");
    } else if (selfDaily != null) {
      total = selfDaily;
      basis = "처음 설문대로";
      evidence.add("처음 설문에서 하루 " + selfDaily + "분 공부할 수 있다고 해서 그만큼 잡았어요");
    } else {
      total = DEFAULT_TOTAL;
      basis = "기본으로";
      evidence.add("아직 기록이 적어서 하루 " + DEFAULT_TOTAL + "분으로 잡았어요");
    }
    if (planOptimismHigh) {
      total *= SHRINK;
      evidence.add("심리검사에서 계획을 크게 잡는 편이라고 나와서 양을 줄였어요");
    }
    int rounded = Math.max(MIN_TOTAL, Math.min(MAX_TOTAL, (int) Math.round(total / 10.0) * 10));
    if (alreadyPlanned > 0) {
      evidence.add("그날 이미 있는 카드 " + alreadyPlanned + "분을 뺐어요");
    }
    return new Total(Math.max(0, rounded - alreadyPlanned), basis, evidence);
  }

  // 학생이 계획 방에서 직접 정한 하루 총량과 근거 (2026-10-25 #431)
  // 기록으로 정하는 총량(totalMinutesOf) 대신 씁니다. 평균 · 설문 · 계획 낙관으로 바꾸지 않습니다. (학생이 정한 것이 먼저)
  //   chosenMinutes: 학생과 정한 오늘 총 공부 분 (요청 검사에서 20 ~ 600) / alreadyPlanned: 그날 이미 있는 카드 시간(분)
  // 한 칸은 30분 이상이라(StudyBlock) 30분보다 적으면 30분으로 잡고, 그날 이미 있는 카드 시간은 똑같이 뺍니다.
  //   (120, 0) → 120     (20, 0) → 30     (120, 60) → 60     (90, 120) → 0
  public static Total chosenTotalOf(int chosenMinutes, int alreadyPlanned) {
    List<String> evidence = new ArrayList<>();
    evidence.add("계획 방에서 오늘 " + chosenMinutes + "분 하기로 해서 그만큼 잡았어요");
    int total = Math.min(MAX_TOTAL, chosenMinutes);
    if (total < MIN_BLOCK) {
      total = MIN_BLOCK;
      evidence.add("한 칸은 " + MIN_BLOCK + "분 이상이라 " + MIN_BLOCK + "분으로 잡았어요");
    }
    if (alreadyPlanned > 0) {
      evidence.add("그날 이미 있는 카드 " + alreadyPlanned + "분을 뺐어요");
    }
    return new Total(Math.max(0, total - alreadyPlanned), "계획 방에서 정한 대로", evidence);
  }

  // 학생이 정한 시작 시각 전은 빼고, 공부할 수 있는 시간을 줄입니다. (#431)
  // 시작 시각이 window 의 시작보다 앞이면 그대로이고, 끝보다 뒤면 길이 0 인 시간(빈 시간 없음)이 됩니다.
  //   window 08:00 ~ 23:00, 21:00 → 21:00 ~ 23:00
  //   window 08:00 ~ 23:00, 07:00 → 08:00 ~ 23:00 (그대로)
  //   window 08:00 ~ 23:00, 23:30 → 23:30 ~ 23:30 (빈 시간 없음)
  public static Span startingAt(Span window, LocalTime startTime) {
    int start = Math.max(window.start(), dayMinutesOf(startTime));
    return new Span(start, Math.max(start, window.end()));
  }

  // 평일 기본 수업 시간 (하루 시작 기준 분)   08:00 ~ 16:30 → [480, 990)
  public static Span classHours() {
    return new Span(dayMinutesOf(CLASS_START), dayMinutesOf(CLASS_END));
  }

  // 두 시간이 조금이라도 겹치는지   [08:00, 16:30) 와 [16:00, 18:00) → true,  [19:00, 21:00) → false
  public static boolean overlaps(Span a, Span b) {
    return a.start() < b.end() && b.start() < a.end();
  }

  // 시각을 하루 시작 기준 분으로   04:00 → 240,  00:00 → 1440,  03:59 → 1679
  public static int dayMinutesOf(LocalTime time) {
    int minutes = time.getHour() * 60 + time.getMinute();
    return minutes >= DAY_START ? minutes : minutes + 24 * 60;
  }

  // 하루 시작 기준 분을 "HH:mm" 으로   1140 → "19:00",  1500 → "01:00"
  public static String timeTextOf(int dayMinutes) {
    int minutes = dayMinutes % (24 * 60);
    return String.format("%02d:%02d", minutes / 60, minutes % 60);
  }

  // 공부할 수 있는 시간 = 수면 보호 시간이 끝나고 1시간 뒤 ~ 잠드는 때. 취침 · 기상을 모르면 00:00 · 06:00 으로 봅니다.
  // 잠이 6시간보다 짧으면 일어나는 시각을 뒤로 밀어 6시간을 지킵니다. (FE 의 getSleepWindow 와 같음)
  //   (23:00, 07:00) → 08:00 ~ 23:00      (01:00, 05:00) → 08:00 ~ 01:00 (6시간 보호 뒤 1시간)
  //   (null, null)   → 07:00 ~ 00:00
  public static Span studyWindowOf(LocalTime sleep, LocalTime wake) {
    LocalTime sleepAt = sleep == null ? DEFAULT_SLEEP : sleep;
    LocalTime wakeAt = wake == null ? DEFAULT_WAKE : wake;
    int sleepClock = sleepAt.getHour() * 60 + sleepAt.getMinute();
    int wakeClock = wakeAt.getHour() * 60 + wakeAt.getMinute();
    int length = Math.max((wakeClock - sleepClock + 24 * 60) % (24 * 60), SLEEP_PROTECT_MIN);
    int start = dayMinutesOf(sleepAt.plusMinutes(length + AFTER_WAKE));
    int end = dayMinutesOf(sleepAt);
    if (end <= start) {
      end = DAY_END; // 새벽 4시가 넘어서 자는 사람은 하루의 끝(다음 날 4시)까지로 봅니다.
    }
    return new Span(start, end);
  }

  // 빈 시간: window 에서 busy(고정 일정 · 그날 카드)를 앞뒤 10분 여유와 함께 뺀 나머지 (시각 순)
  //   window 07:00 ~ 23:00, busy [학교 08:00 ~ 16:30] → [07:00 ~ 07:50], [16:40 ~ 23:00]
  public static List<Span> freeSlotsOf(Span window, List<Span> busy) {
    List<Span> padded = new ArrayList<>();
    for (Span span : busy) {
      padded.add(new Span(span.start() - BUFFER, span.end() + BUFFER));
    }
    padded.sort(Comparator.comparingInt(Span::start));

    List<Span> free = new ArrayList<>();
    int cursor = window.start();
    for (Span span : padded) {
      if (span.start() > cursor) {
        free.add(new Span(cursor, Math.min(span.start(), window.end())));
      }
      cursor = Math.max(cursor, span.end());
      if (cursor >= window.end()) {
        break;
      }
    }
    if (cursor < window.end()) {
      free.add(new Span(cursor, window.end()));
    }
    free.removeIf(span -> span.length() < MIN_BLOCK);
    return free;
  }

  // 빈 시간에 칸을 놓습니다. 칸마다 한 칸 길이(block), 남은 총량이 더 짧으면 그만큼. 30분보다 짧으면 놓지 않습니다.
  // 칸 사이는 10분 띄웁니다. lateFirst(저녁형)면 늦은 빈 시간의 끝에서부터 앞으로 채웁니다. 결과는 시각 순입니다.
  //   free [16:40 ~ 23:00], block 50, total 140 → 16:40(50분) · 17:40(50분) · 18:40(40분)
  //   free [16:40 ~ 23:00], block 50, total 120 → 16:40(50분) · 17:40(50분)  (남은 20분은 30분보다 짧아 놓지 않음)
  //   같은 값에 lateFirst(저녁형)                   → 21:10(50분) · 22:10(50분)  (23:00 에서부터 거꾸로 채움)
  public static List<Placed> placeBlocks(List<Span> free, int block, int total, boolean lateFirst) {
    List<Placed> placed = new ArrayList<>();
    int left = total;
    List<Span> slots = new ArrayList<>(free);
    if (lateFirst) {
      slots.sort(Comparator.comparingInt(Span::end).reversed());
    }
    for (Span slot : slots) {
      int from = slot.start();
      int to = slot.end();
      while (left >= MIN_BLOCK && placed.size() < MAX_BLOCKS) {
        int minutes = Math.min(Math.min(block, left), to - from);
        if (minutes < MIN_BLOCK) {
          break;
        }
        if (lateFirst) {
          placed.add(new Placed(to - minutes, minutes));
          to = to - minutes - BUFFER;
        } else {
          placed.add(new Placed(from, minutes));
          from = from + minutes + BUFFER;
        }
        left -= minutes;
      }
    }
    placed.sort(Comparator.comparingInt(Placed::start));
    return placed;
  }

  // 칸에 넣을 목표들: D-day 가 가까운 목표부터 돌아가며 넣고, 14일 안이면 한 바퀴에 두 번 넣습니다.
  //   [수학(D-5), 영어(D-30)] , 4칸 → 한 바퀴 [수학, 영어, 수학] → [수학, 영어, 수학, 수학]
  // 목표가 없으면 빈 목록입니다. (칸마다 목표 없이 "최근 카드" 로 채움)
  public static List<GoalInfo> goalRotation(List<GoalInfo> goals, int count) {
    List<GoalInfo> ordered = new ArrayList<>(goals);
    ordered.sort(Comparator.comparingInt(GoalInfo::daysLeft).thenComparing(GoalInfo::id));
    List<GoalInfo> round = new ArrayList<>();
    for (GoalInfo goal : ordered) {
      round.add(goal);
      if (goal.daysLeft() <= SOON_DAYS) {
        round.add(goal);
      }
    }
    List<GoalInfo> rotation = new ArrayList<>();
    if (round.isEmpty()) {
      return rotation;
    }
    // 같은 목표가 바로 이어지지 않게, 두 번 넣은 목표는 한 바퀴의 처음과 중간에 둡니다.
    List<GoalInfo> spread = new ArrayList<>();
    List<GoalInfo> second = new ArrayList<>();
    for (GoalInfo goal : round) {
      if (spread.contains(goal)) {
        second.add(goal);
      } else {
        spread.add(goal);
      }
    }
    spread.addAll(second);
    for (int i = 0; i < count; i++) {
      rotation.add(spread.get(i % spread.size()));
    }
    return rotation;
  }

  // 완주형 목표의 하루 분량 = 전체 분량 ÷ 남은 날 수 (올림). 그날도 하루로 셉니다. (FE 의 getDailyPace)
  //   (300, 9) → 30      (100, 0) → 100
  public static int dailyPaceOf(int totalAmount, int daysLeft) {
    int days = Math.max(1, daysLeft + 1);
    return (totalAmount + days - 1) / days;
  }
}
