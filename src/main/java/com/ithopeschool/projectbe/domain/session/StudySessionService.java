package com.ithopeschool.projectbe.domain.session;

import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.care.Stage4Service;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.PlanProgress;
import com.ithopeschool.projectbe.domain.plan.PlanProgress.PlanSession;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.SessionTags.GoodReason;
import com.ithopeschool.projectbe.domain.session.SessionTags.PauseTag;
import com.ithopeschool.projectbe.domain.session.SessionTags.PlayTag;
import com.ithopeschool.projectbe.domain.session.SessionTags.ShortfallReason;
import com.ithopeschool.projectbe.domain.session.SessionTags.WithWhat;
import com.ithopeschool.projectbe.domain.session.dto.PlanActualResponse;
import com.ithopeschool.projectbe.domain.session.dto.SessionEndReportRequest;
import com.ithopeschool.projectbe.domain.session.dto.SessionMemoRequest;
import com.ithopeschool.projectbe.domain.session.dto.SessionPauseRequest;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionCreateRequest;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionResponse;
import com.ithopeschool.projectbe.domain.session.dto.StudyTotalResponse;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 타이머 공부 기록 저장·조회 로직입니다. (이슈 #13 저장, 이슈 #14 조회, 이슈 #224 카드 칸, docs/API.md "공부 기록")
//
// 모든 메서드는 currentUserProvider.getCurrentUserId() 로 시작해서, 로그인하지 않은 요청은 401 이 나갑니다.
// 과목·계획은 "내 것" 인지 확인하고, 겹치는 기록도 조회도 "내 기록" 끼리만 봅니다.
@Service
@RequiredArgsConstructor
public class StudySessionService {

  // 순공이 이보다 짧으면 실수로 누른 것으로 보고 저장하지 않습니다. (FE 타이머와 같은 규칙: 1분)
  static final long MIN_STUDY_MILLIS = 60_000;

  // 기간 조회가 받아 주는 가장 긴 기간(일)입니다. 시작일과 끝일을 모두 센 날 수입니다. (10월 1일 ~ 10월 31일 = 31일)
  // 한 번에 너무 많은 기록을 꺼내지 않도록 정했습니다. (이슈 #14)
  static final int MAX_QUERY_DAYS = 31;

  // 조회할 수 있는 날짜의 범위입니다. 터무니없이 먼 날짜(+999999999-12-31 등)가 DB 까지 가서
  // 500 이 나지 않도록 입구에서 막습니다. 실제 기록은 모두 이 범위 안에 있습니다.
  static final LocalDate EARLIEST_QUERY_DATE = LocalDate.of(1970, 1, 1);
  static final LocalDate LATEST_QUERY_DATE = LocalDate.of(9999, 12, 31);

  // 이보다 앞선 시작 시각은 타이머 기록일 수 없어서 거절합니다. (이 서비스는 2026년에 만들어졌습니다)
  // 폰 시계가 초기화되면 1970년이 찍히는데, 그런 기록이 저장되면 "1970년 ~ 지금" 이 전부 겹쳐서
  // 그 뒤로는 어떤 기록도 저장할 수 없게 됩니다. 운영 DB(PostgreSQL)가 담지 못하는 아주 먼 과거 시각도 여기서 막힙니다.
  static final Instant EARLIEST_START = Instant.parse("2020-01-01T00:00:00Z");

  private final CurrentUserProvider currentUserProvider;
  private final StudySessionRepository studySessionRepository;
  // 기록에 붙일 과목이 "내 과목" 인지 확인할 때 씁니다.
  private final SubjectRepository subjectRepository;
  // 기록에 붙일 계획이 "내 계획" 인지 확인할 때 씁니다.
  private final StudyPlanRepository studyPlanRepository;
  // 기록에 붙일 카드가 "내 카드" 인지 확인할 때 씁니다. (이슈 #224)
  private final CardRepository cardRepository;
  // 관리 4단계면 증빙을 꼭 받고, 증빙이 있는 카드 실행이면 4단계를 풉니다. (이슈 #234)
  private final Stage4Service stage4Service;
  // "지금 몇 시인지" 는 시계에게 묻습니다. 테스트에서 시각을 고정할 수 있습니다. (global/config/ClockConfig)
  private final Clock clock;

  // 공부 기록 하나 저장.
  //
  // 검사 순서: 값의 모양(400) → 내 카드·내 과목·내 계획인지(404) → 이미 있는 기록과 겹치는지(409)
  // 하나라도 걸리면 저장하지 않습니다.
  @Transactional
  public StudySessionResponse create(StudySessionCreateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // kind 를 보내지 않으면 공부 기록입니다.
    PlanKind kind = request.kind() == null ? PlanKind.STUDY : parseKind(request.kind());
    Instant startedAt = Instant.ofEpochMilli(request.startedAt());
    Instant endedAt = Instant.ofEpochMilli(request.endedAt());

    // pauses 를 보냈으면 일시정지 횟수·시간은 보낸 값 대신 pauses 로 계산합니다. (FE summarizePauses 와 같은 규칙)
    // 안 보냈으면(옛 FE) 지금처럼 보낸 횟수·시간을 그대로 씁니다. pausedMs 를 안 보내면 0 입니다.
    List<SessionPause> pauses = request.pauses() == null ? null : toPauses(request.pauses());
    int pauseCount = pauses == null ? request.pauseCount() : pauses.size();
    long pausedMs;
    if (pauses != null) {
      pausedMs = 0;
      for (SessionPause pause : pauses) {
        pausedMs += pause.millis();
      }
    } else {
      pausedMs = request.pausedMs() == null ? 0 : request.pausedMs();
    }

    checkTimes(startedAt, endedAt, pausedMs);
    if (pauses != null) {
      checkPausesInside(pauses, startedAt, endedAt);
    }
    MissReason stopReason = parseStopReason(request.stopReason());
    SessionMode mode = parseMode(request.mode());
    List<SessionMemo> memos = request.memos() == null ? List.of() : toMemos(request.memos());
    // 노는 기록의 종류는 노는 기록에만 저장합니다. 공부 기록에 보냈으면 무시합니다. (과목을 노는 기록에 보낸 것과 같은 약속)
    PlayTag playTag = kind == PlanKind.PLAY ? parsePlayTag(request.playTag()) : null;
    Long overRestMs = kind == PlanKind.PLAY ? request.overRestMs() : null;
    Long cardId = checkCard(userId, request.cardId());
    // 카드로 켠 공부 기록은 과목이 없어도 됩니다. (카드는 과목 대신 목표로 이어집니다, #222)
    Long subjectId = checkSubject(userId, kind, request.subjectId(), cardId != null);
    // 과목을 안 보낸 카드 공부 기록이면 카드의 과목을 채웁니다. 과목별 공부 시간이 '기타' 로 묶이지 않게 하려는 것입니다. (#404)
    // 보낸 과목이 있으면 그 과목이 먼저입니다. 노는 기록은 과목을 두지 않습니다.
    if (subjectId == null && kind == PlanKind.STUDY && cardId != null) {
      subjectId = subjectOfCard(userId, cardId);
    }
    Long planId = checkPlan(userId, request.planId());

    // 내 기록 중에 시간이 겹치는 것이 있으면 거절합니다. (같은 시간을 두 번 세지 않도록)
    // 같은 요청이 동시에 와도 하나만 저장되도록, 겹침을 보기 전에 내 줄을 잠급니다. (StudySessionRepository.lockUser)
    studySessionRepository.lockUser(userId);
    if (studySessionRepository.existsByUserIdAndStartedAtLessThanAndEndedAtGreaterThan(
        userId, endedAt, startedAt)) {
      throw new ApiException(ErrorCode.CONFLICT, "이미 저장된 기록과 시간이 겹쳐요");
    }

    StudySession session =
        new StudySession(
            userId,
            subjectId,
            startedAt,
            endedAt,
            pauseCount,
            pausedMs,
            planId,
            request.targetMinutes(),
            stopReason,
            kind);
    // 카드 기준으로 넓힌 칸을 채웁니다. (#224) 중도 종료(endedEarly)를 안 보냈으면 false 입니다.
    session.recordCard(cardId, mode, Boolean.TRUE.equals(request.endedEarly()));
    if (pauses != null) {
      session.recordPauses(pauses);
    }
    if (request.endReport() != null) {
      applyEndReport(session, request.endReport());
    }
    session.recordMemos(memos);
    session.recordPlay(playTag, overRestMs);

    // 관리 4단계: 공부 기록이면 증빙이 꼭 필요한 세션으로 남기고, 증빙이 있는 카드 실행이면 4단계를 풉니다. (#234)
    boolean stage4On = kind == PlanKind.STUDY && stage4Service.isOn(userId);
    if (stage4On) {
      session.requireProof();
    }
    StudySession saved = studySessionRepository.save(session);
    if (stage4On && cardId != null) {
      stage4Service.releaseIfProven(userId, saved.hasProof());
    }
    return StudySessionResponse.from(saved);
  }

  // 사후 입력 세션 하나 저장. (이슈 #225, POST /api/v1/cards/{id}/late-entry)
  // 카드 날짜 · 마감 · 시간 한도는 CardDayService 가 먼저 검사하고 부릅니다. 여기서는 기록 저장 규칙만 봅니다.
  //   - 시각이 말이 되는지 · 1분 이상인지(400), 내 카드인지(404), 이미 있는 기록과 겹치는지(409)
  //   - 잰 방법은 늘 사후 입력(SELF_REPORT), 일시정지는 없음(0번), 목표 시간은 카드 길이
  @Transactional
  public StudySessionResponse createSelfReport(
      Long cardId,
      int targetMinutes,
      Instant startedAt,
      Instant endedAt,
      SessionEndReportRequest endReport) {
    Long userId = currentUserProvider.getCurrentUserId();

    checkTimes(startedAt, endedAt, 0);
    Long myCardId = checkCard(userId, String.valueOf(cardId));

    studySessionRepository.lockUser(userId);
    if (studySessionRepository.existsByUserIdAndStartedAtLessThanAndEndedAtGreaterThan(
        userId, endedAt, startedAt)) {
      throw new ApiException(ErrorCode.CONFLICT, "이미 저장된 기록과 시간이 겹쳐요");
    }

    // 카드로 켠 공부 기록이라 과목은 카드의 과목입니다. 카드에 과목이 없으면 비어 있습니다. (#404)
    StudySession session =
        new StudySession(
            userId,
            subjectOfCard(userId, myCardId),
            startedAt,
            endedAt,
            0,
            0,
            null,
            targetMinutes,
            null,
            PlanKind.STUDY);
    session.recordCard(myCardId, SessionMode.SELF_REPORT, false);
    session.recordPauses(List.of());
    if (endReport != null) {
      applyEndReport(session, endReport);
    }

    // 관리 4단계: 사후 입력도 증빙이 꼭 필요하고, 증빙이 있으면 4단계를 풉니다. (#234)
    boolean stage4On = stage4Service.isOn(userId);
    if (stage4On) {
      session.requireProof();
    }
    StudySession saved = studySessionRepository.save(session);
    if (stage4On) {
      stage4Service.releaseIfProven(userId, saved.hasProof());
    }
    return StudySessionResponse.from(saved);
  }

  // 기간 안의 내 기록 목록. from ~ to 는 둘 다 포함하는 날짜이고, 없는 기간은 빈 목록입니다.
  //
  // "하루" 는 새벽 4시에 바뀌므로(DayRule) from 날의 04:00 부터 to 다음 날의 04:00 직전까지 "시작한" 기록을 찾습니다.
  // 04:00 를 걸친 기록(03:30 ~ 04:30)은 시작한 날의 기록 하나입니다. 노는 기록도 목록에는 들어 있습니다. (kind 로 구별)
  // 내 공부 기록을 모두 지웁니다. 되돌릴 수 없습니다. 하나도 없어도 그대로 끝납니다. (설정의 "모든 기록 지우기", FE #516)
  //
  // 왜 체크인처럼 한 줄 쿼리(delete from ...)로 지우지 않나요?
  // 일시정지 · 체크한 항목 · 잘 된 이유 · 메모는 따로 저장하는 표(session_pauses 등)에 있고, 그 표가 기록을 가리킵니다.
  // 기록을 불러와서 지우면 JPA 가 딸린 표의 줄부터 지워 줍니다. 내 번호로만 찾아서 다른 사람의 기록은 그대로입니다.
  @Transactional
  public void deleteAll() {
    Long userId = currentUserProvider.getCurrentUserId();
    studySessionRepository.deleteAll(studySessionRepository.findByUserId(userId));
  }

  @Transactional(readOnly = true)
  public List<StudySessionResponse> getSessions(LocalDate from, LocalDate to) {
    Long userId = currentUserProvider.getCurrentUserId();

    checkQueryDate(from);
    checkQueryDate(to);
    if (from.isAfter(to)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시작 날짜는 끝 날짜보다 늦을 수 없어요");
    }
    // 시작일과 끝일을 모두 세어야 해서 1 을 더합니다. (같은 날이면 1일)
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_QUERY_DAYS) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "한 번에 " + MAX_QUERY_DAYS + "일까지만 조회할 수 있어요");
    }

    return studySessionRepository
        .findStartedBetween(userId, DayRule.startOf(from), DayRule.startOf(to.plusDays(1)))
        .stream()
        .map(StudySessionResponse::from)
        .toList();
  }

  // 오늘의 순공시간(분)과 과목별 합계.
  //
  // "오늘" 은 지금 시각을 DayRule 로 바꾼 날짜입니다. 새벽 1시라면 아직 어제입니다. (docs/API.md 2-4)
  // 순공 = 끝 − 시작 − 일시정지한 시간이고, 노는 기록은 넣지 않습니다. (StudyTotal)
  @Transactional(readOnly = true)
  public StudyTotalResponse getTodayTotal() {
    Long userId = currentUserProvider.getCurrentUserId();

    LocalDate today = DayRule.dateOf(clock.instant());
    List<StudySession> sessions =
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(today), DayRule.startOf(today.plusDays(1)));
    return StudyTotalResponse.of(today, StudyTotal.of(sessions));
  }

  // 그날의 내 계획마다 "실제 / 목표" 를 계산합니다. 계획 줄의 "32 / 50분" 과 진행 막대에 씁니다.
  //
  // 노는 계획도 포함합니다. (실제로 얼마나 놀았는지도 보여줘야 합니다 — FE 와 같은 규칙)
  // 계획 하나의 실제 시간 = 그 계획으로 켠 기록(planId 가 같은 기록)의 순공 합. 노는 기록도 더합니다.
  // 기록이 없는 계획도 0 으로 계산해서 돌려줍니다. 계획이 없는 날은 빈 목록입니다.
  // 계산은 PlanProgress 가 합니다. (FE 의 getPlanProgress 와 같은 규칙)
  @Transactional(readOnly = true)
  public List<PlanActualResponse> getPlanActuals(LocalDate date) {
    Long userId = currentUserProvider.getCurrentUserId();

    checkQueryDate(date);
    List<StudyPlan> plans = studyPlanRepository.findByUserIdAndPlanDateOrderById(userId, date);
    if (plans.isEmpty()) {
      return List.of();
    }

    List<Long> planIds = new ArrayList<>();
    for (StudyPlan plan : plans) {
      planIds.add(plan.getId());
    }
    // 기록 하나마다 (어느 계획으로 켰는지, 순공 밀리초) 만 꺼내 PlanProgress 에 넘깁니다.
    List<PlanSession> planSessions = new ArrayList<>();
    for (StudySession session : studySessionRepository.findByUserIdAndPlanIdIn(userId, planIds)) {
      long millis =
          StudyTime.studyMillis(
              session.getStartedAt(), session.getEndedAt(), session.getPausedMs());
      planSessions.add(new PlanSession(session.getPlanId(), millis));
    }

    List<PlanActualResponse> actuals = new ArrayList<>();
    for (StudyPlan plan : plans) {
      long actualMillis = PlanProgress.millisOf(plan.getId(), planSessions);
      actuals.add(
          PlanActualResponse.of(
              plan.getId(), PlanProgress.of(plan.getTargetMinutes(), actualMillis)));
    }
    return actuals;
  }

  // 조회하려는 날짜가 말이 되는 범위인지 봅니다. 아니면 400 입니다. (EARLIEST_QUERY_DATE 설명 참고)
  private void checkQueryDate(LocalDate date) {
    if (date.isBefore(EARLIEST_QUERY_DATE) || date.isAfter(LATEST_QUERY_DATE)) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR,
          "날짜는 " + EARLIEST_QUERY_DATE + " 부터 " + LATEST_QUERY_DATE + " 사이로 보내 주세요");
    }
  }

  // 시각과 일시정지 시간이 말이 되는지 봅니다. 아니면 400 입니다.
  private void checkTimes(Instant startedAt, Instant endedAt, long pausedMs) {
    // 시계가 고장 난 기기에서 온 시각 (EARLIEST_START 설명 참고)
    if (startedAt.isBefore(EARLIEST_START)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시작 시각이 올바르지 않아요");
    }
    // 끝이 시작보다 앞서거나 같은 기록
    if (!endedAt.isAfter(startedAt)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "끝 시각은 시작 시각보다 뒤여야 해요");
    }
    // 아직 오지 않은 시각에 끝난 기록 (끝이 미래가 아니면 시작도 미래가 아닙니다)
    if (endedAt.isAfter(clock.instant())) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "미래 시각은 저장할 수 없어요");
    }
    // 일시정지 시간은 0 이상이고 (끝 − 시작) 보다 작아야 합니다. (0 이상인지는 요청 DTO 가 먼저 봅니다)
    long totalMillis = endedAt.toEpochMilli() - startedAt.toEpochMilli();
    if (pausedMs < 0 || pausedMs >= totalMillis) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "일시정지 시간은 기록 길이보다 짧아야 해요");
    }
    // 1분 미만은 순공(끝 − 시작 − 일시정지) 기준으로 봅니다. 순공 계산은 StudyTime 이 합니다.
    if (StudyTime.studyMillis(startedAt, endedAt, pausedMs) < MIN_STUDY_MILLIS) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "1분 미만의 기록은 저장하지 않아요");
    }
  }

  // 종류에 맞는 과목 번호를 돌려줍니다. (계획과 같은 규칙)
  // - 노는 기록: 과목이 없습니다. 보냈더라도 null 로 저장합니다.
  // - 공부 기록: 과목이 꼭 있어야 하고(400), 내 과목이어야 합니다(404).
  //   단, 카드로 켠 공부 기록(hasCard)은 과목이 없어도 됩니다. 보냈으면 내 과목이어야 합니다.
  private Long checkSubject(Long userId, PlanKind kind, String subjectId, boolean hasCard) {
    if (kind == PlanKind.PLAY) {
      return null;
    }
    if (subjectId == null || subjectId.isEmpty()) {
      if (hasCard) {
        return null;
      }
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "공부 기록은 과목이나 카드를 골라 주세요");
    }
    // 숫자 모양인지는 요청 DTO 의 @Pattern 이 이미 검사했습니다.
    Long id = Long.valueOf(subjectId);
    // 없는 과목과 남의 과목은 구별하지 않고 똑같이 404 입니다. (과목 API 와 같은 방식)
    if (subjectRepository.findByIdAndUserId(id, userId).isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없어요");
    }
    return id;
  }

  // 계획 번호를 돌려줍니다. 안 보냈으면(null 또는 "") 계획 없이 켠 타이머라 null 입니다.
  // 보냈으면 내 계획이어야 합니다. 없는 계획과 남의 계획은 구별하지 않고 똑같이 404 입니다.
  private Long checkPlan(Long userId, String planId) {
    if (planId == null || planId.isEmpty()) {
      return null;
    }
    Long id = Long.valueOf(planId);
    if (studyPlanRepository.findByIdAndUserId(id, userId).isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "계획을 찾을 수 없어요");
    }
    return id;
  }

  // 카드 번호를 돌려줍니다. 안 보냈으면(null 또는 "") 카드 없이 켠 기록이라 null 입니다.
  // 보냈으면 내 카드여야 합니다. 없는 카드와 남의 카드는 구별하지 않고 똑같이 404 입니다.
  // ⚠️ 이슈 #224 는 "cardId 없는 새 세션은 400" 이지만, 지금 FE 타이머가 아직 cardId 없이 보내서 당분간 받아 줍니다.
  private Long checkCard(Long userId, String cardId) {
    if (cardId == null || cardId.isEmpty()) {
      return null;
    }
    Long id = Long.valueOf(cardId);
    if (cardRepository.findByIdAndUserId(id, userId).isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "카드를 찾을 수 없어요");
    }
    return id;
  }

  // 내 카드의 과목 번호를 돌려줍니다. 카드에 과목이 없으면 null 입니다. (#404)
  // checkCard 로 내 카드인 것을 이미 확인한 번호를 받습니다.
  // 과목을 지우면 카드의 과목도 비워지므로(SubjectService.delete) 여기서 나오는 과목은 늘 있는 내 과목입니다.
  private Long subjectOfCard(Long userId, Long cardId) {
    return cardRepository.findByIdAndUserId(cardId, userId).map(Card::getSubjectId).orElse(null);
  }

  // 일시정지 요청을 저장할 값으로 바꿉니다. 이유 태그가 7가지가 아니면 400 입니다. 빈 메모("")는 null 입니다.
  private List<SessionPause> toPauses(List<SessionPauseRequest> requests) {
    List<SessionPause> pauses = new ArrayList<>();
    for (SessionPauseRequest request : requests) {
      PauseTag tag = PauseTag.fromValue(request.tag());
      if (tag == null) {
        throw new ApiException(
            ErrorCode.VALIDATION_ERROR,
            "일시정지 이유는 toilet, snack, phone, mindWander, stuck, external, other 중에서 골라 주세요");
      }
      String note = request.note() == null || request.note().isEmpty() ? null : request.note();
      pauses.add(
          new SessionPause(
              Instant.ofEpochMilli(request.startedAt()),
              Instant.ofEpochMilli(request.endedAt()),
              tag,
              note));
    }
    return pauses;
  }

  // 일시정지 시각이 모두 기록의 시작 ~ 끝 사이인지 봅니다. 아니면 400 입니다.
  // (일시정지한 채로 끝냈으면 FE 가 끝을 기록이 끝난 시각으로 보내므로, 끝과 같은 것은 괜찮습니다)
  private void checkPausesInside(List<SessionPause> pauses, Instant startedAt, Instant endedAt) {
    for (SessionPause pause : pauses) {
      boolean isInside =
          !pause.getStartedAt().isBefore(startedAt)
              && !pause.getEndedAt().isBefore(startedAt)
              && !pause.getStartedAt().isAfter(endedAt)
              && !pause.getEndedAt().isAfter(endedAt);
      if (!isInside) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "일시정지 시각은 기록의 시작과 끝 사이여야 해요");
      }
    }
  }

  // 메모 요청을 저장할 값으로 바꿉니다. 쓴 시각이 말이 안 되면(2020년 전 · 미래) 400 입니다.
  private List<SessionMemo> toMemos(List<SessionMemoRequest> requests) {
    List<SessionMemo> memos = new ArrayList<>();
    for (SessionMemoRequest request : requests) {
      Instant at = Instant.ofEpochMilli(request.at());
      if (!isReasonableTime(at)) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "메모를 쓴 시각이 올바르지 않아요");
      }
      memos.add(new SessionMemo(at, request.text()));
    }
    return memos;
  }

  // 종료 팝업에서 받은 것을 검사해서 기록에 적습니다.
  private void applyEndReport(StudySession session, SessionEndReportRequest report) {
    // 체크한 항목: id(40자까지) → 체크한 시각. 안 보냈으면 하나도 체크하지 않은 것입니다.
    Map<String, Instant> checked = new LinkedHashMap<>();
    if (report.checked() != null) {
      for (Map.Entry<String, Long> entry : report.checked().entrySet()) {
        String itemId = entry.getKey();
        if (itemId == null || itemId.isBlank() || itemId.length() > 40) {
          throw new ApiException(ErrorCode.VALIDATION_ERROR, "체크한 항목의 id 는 1 ~ 40자로 보내 주세요");
        }
        if (entry.getValue() == null || !isReasonableTime(Instant.ofEpochMilli(entry.getValue()))) {
          throw new ApiException(ErrorCode.VALIDATION_ERROR, "체크한 시각이 올바르지 않아요");
        }
        checked.put(itemId, Instant.ofEpochMilli(entry.getValue()));
      }
    }

    ShortfallReason shortfallReason = null;
    if (report.shortfallReason() != null) {
      shortfallReason = ShortfallReason.fromValue(report.shortfallReason());
      if (shortfallReason == null) {
        throw new ApiException(
            ErrorCode.VALIDATION_ERROR,
            "못 한 이유는 tooMuch, stuck, phone, mindWander, tired, external, etc 중에서 골라 주세요");
      }
    }

    WithWhat withWhat = null;
    if (report.withWhat() != null) {
      withWhat = WithWhat.fromValue(report.withWhat());
      if (withWhat == null) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "같이 한 것은 music, video, none 중에서 골라 주세요");
      }
    }

    Set<GoodReason> goodReasons = EnumSet.noneOf(GoodReason.class);
    if (report.goodReasons() != null) {
      for (String value : report.goodReasons()) {
        GoodReason reason = GoodReason.fromValue(value);
        if (reason == null) {
          throw new ApiException(
              ErrorCode.VALIDATION_ERROR,
              "잘 된 이유는 focus, goodPlan, condition, environment, helped, luck 중에서 골라 주세요");
        }
        goodReasons.add(reason);
      }
    }

    // 증빙은 빈 글자("")면 없는 것(null)으로 적습니다.
    String proofLink = null;
    String proofSummary = null;
    if (report.proof() != null) {
      proofLink = emptyToNull(report.proof().link());
      proofSummary = emptyToNull(report.proof().summary());
    }

    session.recordEndReport(
        checked, shortfallReason, withWhat, proofLink, proofSummary, goodReasons);
  }

  // 시각이 말이 되는지 봅니다. 서비스를 만든 2020년보다 앞서거나, 아직 오지 않은 시각이면 false 입니다.
  private boolean isReasonableTime(Instant time) {
    return !time.isBefore(EARLIEST_START) && !time.isAfter(clock.instant());
  }

  // FE 가 보낸 글자("offline")를 세션 방법으로 바꿉니다. 안 보냈으면 타이머(NORMAL)입니다.
  // 사후 입력(selfReport)은 여기서 받지 않습니다. 사후 입력에는 따로 지켜야 할 기한·한도가 있어서
  // POST /api/v1/cards/{id}/late-entry (createSelfReport) 로만 받습니다. (#225)
  private SessionMode parseMode(String value) {
    if (value == null) {
      return SessionMode.NORMAL;
    }
    SessionMode mode = SessionMode.fromValue(value);
    if (mode == SessionMode.SELF_REPORT) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "사후 입력은 카드의 사후 입력(late-entry)으로 보내 주세요");
    }
    if (mode == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "세션 방법은 normal, offline 중에서 골라 주세요");
    }
    return mode;
  }

  // FE 가 보낸 글자("game")를 노는 기록의 종류로 바꿉니다. 안 보냈으면 null, 6가지가 아니면 400 입니다.
  private PlayTag parsePlayTag(String value) {
    if (value == null) {
      return null;
    }
    PlayTag tag = PlayTag.fromValue(value);
    if (tag == null) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "노는 종류는 youtube, game, sns, webtoon, rest, etc 중에서 골라 주세요");
    }
    return tag;
  }

  // 빈 글자("")를 null 로 바꿉니다.
  private String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  // FE 가 보낸 글자("study")를 종류로 바꿉니다. 두 가지가 아니면 400 입니다.
  private PlanKind parseKind(String value) {
    PlanKind kind = PlanKind.fromValue(value);
    if (kind == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "기록 종류는 study, play 중에서 골라 주세요");
    }
    return kind;
  }

  // FE 가 보낸 글자("phone")를 그만둔 이유로 바꿉니다. 안 보냈으면 null, 8가지(MissReason)가 아니면 400 입니다.
  private MissReason parseStopReason(String value) {
    if (value == null) {
      return null;
    }
    MissReason reason = MissReason.fromValue(value);
    if (reason == null) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR,
          "그만둔 이유는 tired, event, phone, tooMuch, unwilling, sick, procrastinated, etc 중에서 골라 주세요");
    }
    return reason;
  }
}
