package com.ithopeschool.projectbe.domain.quizresult;

import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.quizresult.dto.QuizResultRequest;
import com.ithopeschool.projectbe.domain.quizresult.dto.QuizResultResponse;
import com.ithopeschool.projectbe.domain.quizresult.dto.QuizStatsResponse;
import com.ithopeschool.projectbe.domain.quizresult.dto.QuizStatsResponse.DayStats;
import com.ithopeschool.projectbe.domain.quizresult.dto.QuizStatsResponse.SubjectStats;
import com.ithopeschool.projectbe.domain.quizresult.dto.QuizStatsResponse.TotalDayStats;
import com.ithopeschool.projectbe.domain.quizresult.dto.QuizStatsResponse.UnitDayStats;
import com.ithopeschool.projectbe.domain.quizresult.dto.QuizStatsResponse.UnitStats;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.session.StudyTime;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 확인 문제(퀴즈) 결과 저장과 통계 탭의 기간 통계입니다. (2026-10-24)
@Service
@RequiredArgsConstructor
public class QuizResultService {

  // 기간 통계가 받아 주는 가장 긴 기간(일)입니다. (공부 기록 조회와 같음)
  static final int MAX_QUERY_DAYS = 31;

  private final CurrentUserProvider currentUserProvider;
  private final QuizResultRepository quizResultRepository;
  private final SubjectRepository subjectRepository;
  private final StudySessionRepository studySessionRepository;
  // "오늘" 이 언제인지 시계에게 묻습니다. 테스트에서 시각을 고정할 수 있습니다.
  private final Clock clock;

  // 퀴즈 한 번의 결과를 저장합니다. 날짜는 지금 시각으로 정합니다. (하루는 새벽 4시 기준)
  // 검사 순서: 맞은 개수 > 전체 개수 · 걸린 시간 개수 ≠ 전체 개수 (400) → 없는 과목 · 남의 과목 (404)
  @Transactional
  public QuizResultResponse save(QuizResultRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    if (request.correctCount() > request.totalCount()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "맞은 개수가 전체 개수보다 많을 수 없어요");
    }
    if (request.questionSeconds().size() != request.totalCount()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "문제마다 걸린 시간을 문제 수만큼 보내 주세요");
    }
    // 없는 과목과 남의 과목은 구별하지 않고 똑같이 404 입니다. (과목 API 와 같은 방식)
    if (subjectRepository.findByIdAndUserId(request.subjectId(), userId).isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없어요");
    }

    QuizResult result =
        new QuizResult(
            userId,
            request.subjectId(),
            request.unit().trim(),
            DayRule.dateOf(clock.instant()),
            request.correctCount(),
            request.totalCount(),
            request.questionSeconds());
    return QuizResultResponse.from(quizResultRepository.save(result));
  }

  // from ~ to (두 날짜 모두 포함) 의 과목 · 단원별 퀴즈 통계와 과목별 공부 시간. 31일까지.
  // 내 과목은 퀴즈가 없어도 모두 담습니다. (공부 시간만 있는 과목도 그래프에 보이게)
  @Transactional(readOnly = true)
  public QuizStatsResponse getStats(LocalDate from, LocalDate to) {
    Long userId = currentUserProvider.getCurrentUserId();
    if (from.isAfter(to)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시작 날짜는 끝 날짜보다 늦을 수 없어요");
    }
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_QUERY_DAYS) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "한 번에 " + MAX_QUERY_DAYS + "일까지만 조회할 수 있어요");
    }

    List<LocalDate> dates = new ArrayList<>();
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      dates.add(date);
    }
    List<QuizResult> results =
        quizResultRepository.findByUserIdAndQuizDateBetweenOrderByQuizDateAscIdAsc(
            userId, from, to);
    // 공부 기록은 "시작한 날" 로 셉니다. to 날의 새벽 4시 다음 날까지가 기간입니다.
    List<StudySession> sessions =
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(from), DayRule.startOf(to.plusDays(1)));

    List<SubjectStats> subjects = new ArrayList<>();
    for (Subject subject : subjectRepository.findByUserIdOrderById(userId)) {
      List<QuizResult> subjectResults = new ArrayList<>();
      for (QuizResult result : results) {
        if (result.getSubjectId().equals(subject.getId())) {
          subjectResults.add(result);
        }
      }
      subjects.add(
          new SubjectStats(
              subject.getId(),
              subject.getName(),
              subject.getColor().toValue(),
              dayStats(dates, subjectResults, studyMillisByDate(sessions, subject.getId())),
              unitStats(dates, subjectResults)));
    }
    return new QuizStatsResponse(from, to, totalDays(dates, sessions), subjects);
  }

  // 날짜별 전체 순공시간. 노는 기록(PLAY)만 빼고, 과목이 없는 기록(카드 타이머)도 셉니다.
  private static List<TotalDayStats> totalDays(List<LocalDate> dates, List<StudySession> sessions) {
    Map<LocalDate, Long> millisByDate = new LinkedHashMap<>();
    for (StudySession session : sessions) {
      if (session.getKind() == PlanKind.PLAY) {
        continue;
      }
      long millis =
          StudyTime.studyMillis(
              session.getStartedAt(), session.getEndedAt(), session.getPausedMs());
      millisByDate.merge(DayRule.dateOf(session.getStartedAt()), millis, Long::sum);
    }
    List<TotalDayStats> days = new ArrayList<>();
    for (LocalDate date : dates) {
      days.add(new TotalDayStats(date, StudyTime.toMinutes(millisByDate.getOrDefault(date, 0L))));
    }
    return days;
  }

  // 이 과목의 날짜별 순공시간(밀리초). 노는 기록(PLAY)과 과목 없는 기록은 빼고 셉니다.
  private static Map<LocalDate, Long> studyMillisByDate(
      List<StudySession> sessions, Long subjectId) {
    Map<LocalDate, Long> millisByDate = new LinkedHashMap<>();
    for (StudySession session : sessions) {
      boolean isPlay = session.getKind() == PlanKind.PLAY;
      if (isPlay || !subjectId.equals(session.getSubjectId())) {
        continue;
      }
      LocalDate date = DayRule.dateOf(session.getStartedAt());
      long millis =
          StudyTime.studyMillis(
              session.getStartedAt(), session.getEndedAt(), session.getPausedMs());
      millisByDate.merge(date, millis, Long::sum);
    }
    return millisByDate;
  }

  // 과목의 날짜별 맞은 개수 · 전체 개수 · 공부 시간. 기간의 모든 날짜를 담습니다.
  private static List<DayStats> dayStats(
      List<LocalDate> dates, List<QuizResult> results, Map<LocalDate, Long> studyMillisByDate) {
    List<DayStats> days = new ArrayList<>();
    for (LocalDate date : dates) {
      int correct = 0;
      int total = 0;
      for (QuizResult result : results) {
        if (result.getQuizDate().equals(date)) {
          correct += result.getCorrectCount();
          total += result.getTotalCount();
        }
      }
      // 밀리초로 모두 더한 뒤 마지막에 한 번만 분으로 바꿉니다. (StudyTime.toMinutes 설명)
      int minutes = StudyTime.toMinutes(studyMillisByDate.getOrDefault(date, 0L));
      days.add(new DayStats(date, correct, total, minutes));
    }
    return days;
  }

  // 과목 안 단원별 통계. 처음 나온 순서대로 담습니다.
  private static List<UnitStats> unitStats(List<LocalDate> dates, List<QuizResult> results) {
    Map<String, List<QuizResult>> resultsByUnit = new LinkedHashMap<>();
    for (QuizResult result : results) {
      resultsByUnit.computeIfAbsent(result.getUnit(), unit -> new ArrayList<>()).add(result);
    }

    List<UnitStats> units = new ArrayList<>();
    for (Map.Entry<String, List<QuizResult>> entry : resultsByUnit.entrySet()) {
      int correct = 0;
      int total = 0;
      int seconds = 0;
      for (QuizResult result : entry.getValue()) {
        correct += result.getCorrectCount();
        total += result.getTotalCount();
        seconds += result.totalSeconds();
      }
      // 전체 개수는 1 이상만 저장되므로 0 으로 나누는 일은 없습니다.
      int averageSeconds = Math.round((float) seconds / total);

      List<UnitDayStats> days = new ArrayList<>();
      for (LocalDate date : dates) {
        int dayCorrect = 0;
        int dayTotal = 0;
        for (QuizResult result : entry.getValue()) {
          if (result.getQuizDate().equals(date)) {
            dayCorrect += result.getCorrectCount();
            dayTotal += result.getTotalCount();
          }
        }
        days.add(new UnitDayStats(date, dayCorrect, dayTotal));
      }
      units.add(new UnitStats(entry.getKey(), correct, total, averageSeconds, days));
    }
    return units;
  }
}
