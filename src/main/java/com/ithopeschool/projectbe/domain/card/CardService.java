package com.ithopeschool.projectbe.domain.card;

import com.ithopeschool.projectbe.domain.card.dto.CardAlertRequest;
import com.ithopeschool.projectbe.domain.card.dto.CardCreateRequest;
import com.ithopeschool.projectbe.domain.card.dto.CardResponse;
import com.ithopeschool.projectbe.domain.card.dto.CardUpdateRequest;
import com.ithopeschool.projectbe.domain.card.dto.ChecklistItemRequest;
import com.ithopeschool.projectbe.domain.fixedblock.FixedBlockRepository;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswer;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswerRepository;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 카드 목록·만들기·바꾸기·지우기 로직입니다. (이슈 #222, docs/API.md "카드")
//
// 모든 메서드는 currentUserProvider.getCurrentUserId() 로 시작합니다. 그래서 로그인하지 않은 요청은 401 이 나가고,
// 모든 조회·수정에 "내 userId" 조건이 붙어서 남의 카드는 보이지도, 바뀌지도 않습니다.
// (남의 카드 번호를 보내면 "그런 번호가 없다" 는 것과 똑같이 404 로 답합니다)
@Service
@RequiredArgsConstructor
public class CardService {

  // 그날 카드의 정렬 기준: 시작 시각이 빠른 순 → 시작 시각이 없는 카드(null)는 뒤로 → 같으면 만든 순서(번호 순)
  // FE 의 getCardsOnDate (src/lib/cards.ts) 와 같은 순서입니다.
  private static final Comparator<Card> CARD_ORDER =
      Comparator.comparing(
              Card::getStartTime, Comparator.nullsLast(Comparator.<LocalTime>naturalOrder()))
          .thenComparing(Card::getId);

  private final CurrentUserProvider currentUserProvider;
  private final CardRepository cardRepository;
  // "지금 몇 시인지" 는 시계에게 묻습니다. 테스트에서 시각을 고정할 수 있습니다. (global/config/ClockConfig)
  private final Clock clock;
  // 수면 보호 시간(설문의 취침 · 기상)과 10분 버퍼(고정 일정)를 볼 때 씁니다. (이슈 #223)
  private final OnboardingAnswerRepository onboardingAnswerRepository;
  private final FixedBlockRepository fixedBlockRepository;
  // 카드에 붙인 과목이 "내 과목" 인지 볼 때 씁니다. (이슈 #404)
  private final SubjectRepository subjectRepository;

  // 내 카드 목록.
  // - date 를 보내면: 그날 하는 카드만, 시작 시각 순서로 (1회성은 그 날짜, 반복은 시작한 날부터 그 요일)
  // - date 를 안 보내면: 내 카드 전부, 만든 순서로
  // 카드가 없으면 빈 목록입니다.
  @Transactional(readOnly = true)
  public List<CardResponse> getCards(LocalDate date) {
    Long userId = currentUserProvider.getCurrentUserId();

    List<CardResponse> responses = new ArrayList<>();
    if (date == null) {
      for (Card card : cardRepository.findByUserIdOrderById(userId)) {
        responses.add(CardResponse.from(card));
      }
      return responses;
    }

    // DB 에서 후보(그날의 1회성 카드 + 이미 시작한 반복 카드)를 꺼낸 뒤, 요일까지 맞는 카드만 남깁니다.
    List<Card> cardsOnDate = new ArrayList<>();
    for (Card card : cardRepository.findCandidatesOn(userId, date)) {
      if (card.isOnDate(date)) {
        cardsOnDate.add(card);
      }
    }
    cardsOnDate.sort(CARD_ORDER);
    for (Card card : cardsOnDate) {
      responses.add(CardResponse.from(card));
    }
    return responses;
  }

  // 카드 한 장 만들기.
  // 보낸 값을 모두 검사한 뒤에 저장합니다. 하나라도 틀리면 저장하지 않습니다.
  @Transactional
  public CardResponse create(CardCreateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    List<ChecklistItem> checklist = toChecklist(request.checklist(), Set.of());
    Card card =
        new Card(
            userId,
            request.title(),
            request.date(),
            request.durationMinutes(),
            checklist,
            clock.instant());

    if (request.repeatDays() != null) {
      card.changeRepeatDays(checkRepeatDays(request.repeatDays()));
    }
    card.changeStartTime(parseStartTime(request.startTime()));
    card.changeGoalId(parseGoalId(request.goalId()));
    card.changeSubjectId(checkSubject(userId, request.subjectId()));
    // priority · type 을 안 보내면 만들 때 정한 기본값(유연 · 자습) 그대로입니다.
    if (request.priority() != null) {
      card.changePriority(parsePriority(request.priority()));
    }
    if (request.type() != null) {
      card.changeType(parseType(request.type()));
    }
    card.changeExpectedDone(request.expectedDone());
    card.changeExpectedDoneReason(emptyToNull(request.expectedDoneReason()));
    if (request.expectedFailCauses() != null) {
      card.changeExpectedFailCauses(request.expectedFailCauses());
    }
    if (request.alert() != null) {
      applyAlert(card, request.alert());
    }
    card.changePlanningMs(request.planningMs());

    checkExpectedDone(card);
    checkSleep(userId, card);
    Card saved = cardRepository.save(card);
    return CardResponse.from(saved, bufferWarningOf(userId, saved));
  }

  // 카드 바꾸기: 보낸 것만 바꿉니다. 목록(반복 요일 · 체크리스트 · 예상 실패 원인)은 보낸 목록으로 통째로 바꿉니다.
  @Transactional
  public CardResponse update(Long id, CardUpdateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    Card card = findMyCard(userId, id);

    if (request.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "바꿀 값을 보내 주세요");
    }

    if (request.title() != null) {
      card.changeTitle(request.title());
    }
    if (request.date() != null) {
      card.changeCardDate(request.date());
    }
    if (request.repeatDays() != null) {
      // 빈 목록([])이면 1회성 카드가 됩니다.
      card.changeRepeatDays(checkRepeatDays(request.repeatDays()));
    }
    if (request.startTime() != null) {
      // 빈 글자("")면 parseStartTime 이 null 을 돌려줘서 "시작 시각 없음" 이 됩니다.
      card.changeStartTime(parseStartTime(request.startTime()));
    }
    if (request.durationMinutes() != null) {
      card.changeDurationMinutes(request.durationMinutes());
    }
    if (request.checklist() != null) {
      // 원래 항목의 id 는 새 항목에 다시 붙이지 않습니다. (지난 세션 기록의 "체크한 항목" 이 엉뚱한 항목을 가리키지 않게)
      Set<String> oldIds = new HashSet<>();
      for (ChecklistItem item : card.getChecklist()) {
        oldIds.add(item.getItemKey());
      }
      card.changeChecklist(toChecklist(request.checklist(), oldIds));
    }
    if (request.goalId() != null) {
      // 빈 글자("")면 "목표 없음" 이 됩니다.
      card.changeGoalId(parseGoalId(request.goalId()));
    }
    if (request.subjectId() != null) {
      // 빈 글자("")면 "과목 없음" 이 됩니다.
      card.changeSubjectId(checkSubject(userId, request.subjectId()));
    }
    if (request.priority() != null) {
      card.changePriority(parsePriority(request.priority()));
    }
    if (request.type() != null) {
      card.changeType(parseType(request.type()));
    }
    if (request.expectedDone() != null) {
      card.changeExpectedDone(request.expectedDone());
    }
    if (request.expectedDoneReason() != null) {
      // 빈 글자("")면 이유를 지웁니다.
      card.changeExpectedDoneReason(emptyToNull(request.expectedDoneReason()));
    }
    if (request.expectedFailCauses() != null) {
      card.changeExpectedFailCauses(request.expectedFailCauses());
    }
    if (request.alert() != null) {
      applyAlert(card, request.alert());
    }
    if (request.planningMs() != null) {
      card.changePlanningMs(request.planningMs());
    }

    // 바뀐 뒤의 모습으로 한 번 더 봅니다. (체크리스트만 줄여도 예상 달성 개수가 넘칠 수 있음)
    // 여기서 400 이 나면 @Transactional 이 위에서 바꾼 값을 모두 되돌립니다.
    checkExpectedDone(card);
    // 시각이나 길이를 바꿀 때만 수면 보호를 봅니다. (제목만 고치는데 예전 카드라 막히지 않게)
    if (request.startTime() != null || request.durationMinutes() != null) {
      checkSleep(userId, card);
    }
    // 엔티티의 값을 바꾸기만 하면 이 메서드가 끝날 때 JPA 가 DB 에 반영합니다.
    return CardResponse.from(card, bufferWarningOf(userId, card));
  }

  // 카드 시간이 수면 보호 시간과 겹치면 400 입니다. 시작 시각이 없으면 보지 않습니다. (이슈 #223, ScheduleCheck)
  // 수면 보호 시간은 가장 최근 설문의 취침 · 기상 시각이고, 없으면 00:00 ~ 06:00 입니다. 6시간보다 짧으면 6시간으로 늘립니다.
  private void checkSleep(Long userId, Card card) {
    if (card.getStartTime() == null) {
      return;
    }
    Optional<OnboardingAnswer> answer =
        onboardingAnswerRepository.findFirstByUserIdOrderByAnsweredAtDescIdDesc(userId);
    ScheduleCheck.SleepWindow window =
        ScheduleCheck.sleepWindow(
            answer.map(OnboardingAnswer::getSleepTime).orElse(null),
            answer.map(OnboardingAnswer::getWakeUpTime).orElse(null));
    if (ScheduleCheck.isOverlappingSleep(card.getStartTime(), card.getDurationMinutes(), window)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "수면 보호 시간(" + window.label() + ")과 겹쳐요");
    }
  }

  // 앞뒤 카드 · 고정 일정과 10분 여유가 없으면 경고 문장, 넉넉하면 null 입니다. (이슈 #223, 막지 않음)
  // 반복 카드는 시작 날짜부터 일주일을 보므로, 그 기간의 카드를 한 번에 꺼냅니다.
  private String bufferWarningOf(Long userId, Card card) {
    if (card.getStartTime() == null) {
      return null;
    }
    LocalDate from = card.getCardDate();
    LocalDate to = card.isOneOff() ? from : from.plusDays(6);
    return ScheduleCheck.bufferWarning(
        from,
        card.repeatDayList(),
        card.getStartTime(),
        card.getDurationMinutes(),
        cardRepository.findCandidatesBetween(userId, from, to),
        fixedBlockRepository.findByUserIdOrderById(userId),
        card.getId());
  }

  // 카드 지우기. 없는 번호이거나 남의 카드면 404 입니다.
  @Transactional
  public void delete(Long id) {
    Long userId = currentUserProvider.getCurrentUserId();
    cardRepository.delete(findMyCard(userId, id));
  }

  // 내 카드 중에서 번호로 찾습니다. 없는 번호이거나 남의 카드면 똑같이 404 입니다.
  private Card findMyCard(Long userId, Long id) {
    return cardRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "카드를 찾을 수 없어요"));
  }

  // 체크리스트 요청을 저장할 항목으로 바꿉니다.
  // 1) 보낸 id 가 서로 겹치면 400 입니다. (어느 항목을 체크했는지 구별할 수 없게 되기 때문)
  // 2) id 를 안 보낸 항목에는 "i1", "i2" … 중에서 아직 안 쓴 것을 붙입니다.
  //    reservedIds 는 붙이면 안 되는 id 입니다. (바꾸기에서는 원래 항목들의 id)
  //
  //   [ {text:"a"}, {id:"i1", text:"b"}, {text:"c"} ]  →  i2 · i1 · i3
  private List<ChecklistItem> toChecklist(
      List<ChecklistItemRequest> items, Set<String> reservedIds) {
    Set<String> sentIds = new HashSet<>();
    for (ChecklistItemRequest item : items) {
      if (item.id() != null && !item.id().isEmpty() && !sentIds.add(item.id())) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "체크리스트 항목 id 가 겹치지 않게 보내 주세요");
      }
    }

    List<ChecklistItem> checklist = new ArrayList<>();
    int next = 1;
    for (ChecklistItemRequest item : items) {
      String itemId = item.id();
      if (itemId == null || itemId.isEmpty()) {
        // 보낸 id · 원래 id 와 겹치지 않는 "i숫자" 를 찾습니다.
        while (sentIds.contains("i" + next) || reservedIds.contains("i" + next)) {
          next++;
        }
        itemId = "i" + next;
        sentIds.add(itemId);
      }
      checklist.add(new ChecklistItem(itemId, item.text()));
    }
    return checklist;
  }

  // 반복 요일이 겹치지 않는지 봅니다. 겹치면 400 입니다. (0 ~ 6 인지는 요청 DTO 가 먼저 봅니다)
  private List<Integer> checkRepeatDays(List<Integer> days) {
    Set<Integer> seen = new HashSet<>();
    for (Integer day : days) {
      if (!seen.add(day)) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "반복 요일이 겹치지 않게 보내 주세요");
      }
    }
    return days;
  }

  // 예상 달성 개수가 체크리스트 개수보다 많으면 400 입니다.
  private void checkExpectedDone(Card card) {
    Integer expectedDone = card.getExpectedDone();
    int checklistSize = card.getChecklist().size();
    if (expectedDone != null && expectedDone > checklistSize) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "예상 달성 개수는 체크리스트 개수(" + checklistSize + "개) 이하로 보내 주세요");
    }
  }

  // 알림 세 칸을 검사해서 넣습니다. 문구가 빈 글자("")면 기본 문구(null)입니다.
  private void applyAlert(Card card, CardAlertRequest alert) {
    AlertMethod method = AlertMethod.fromValue(alert.method());
    if (method == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "알림 방법은 message, call 중에서 골라 주세요");
    }
    card.changeAlert(method, alert.minutesBefore(), emptyToNull(alert.text()));
  }

  // FE 가 보낸 글자("absolute")를 우선순위로 바꿉니다. 두 가지가 아니면 400 입니다.
  private CardPriority parsePriority(String value) {
    CardPriority priority = CardPriority.fromValue(value);
    if (priority == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "우선순위는 absolute, flexible 중에서 골라 주세요");
    }
    return priority;
  }

  // FE 가 보낸 글자("lecture")를 유형으로 바꿉니다. 두 가지가 아니면 400 입니다.
  private CardType parseType(String value) {
    CardType type = CardType.fromValue(value);
    if (type == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "카드 유형은 self, lecture 중에서 골라 주세요");
    }
    return type;
  }

  // "19:00" 을 시각으로 바꿉니다. 보내지 않았거나(null) 빈 글자("")면 "시작 시각 없음"(null) 입니다.
  // 모양("HH:mm")은 요청 DTO 의 @Pattern 이 이미 검사했습니다.
  private LocalTime parseStartTime(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    return LocalTime.parse(value);
  }

  // 목표 번호를 숫자로 바꿉니다. 보내지 않았거나(null) 빈 글자("")면 "목표 없음"(null) 입니다.
  // 숫자 모양인지는 요청 DTO 의 @Pattern 이 이미 검사했습니다.
  // ⚠️ 목표 테이블(#221)이 아직 없어서 "내 목표인지" 는 검사하지 않습니다. 목표가 생기면 여기서 404 를 던지게 고칩니다.
  private Long parseGoalId(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    return Long.valueOf(value);
  }

  // 과목 번호를 숫자로 바꿉니다. 보내지 않았거나(null) 빈 글자("")면 "과목 없음"(null) 입니다. (이슈 #404)
  // 숫자 모양인지는 요청 DTO 의 @Pattern 이 이미 검사했습니다.
  // 없는 과목과 남의 과목은 구별하지 않고 똑같이 404 입니다. (계획 · 공부 기록의 과목과 같은 방식)
  private Long checkSubject(Long userId, String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    Long id = Long.valueOf(value);
    if (subjectRepository.findByIdAndUserId(id, userId).isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없어요");
    }
    return id;
  }

  // 빈 글자("")를 null 로 바꿉니다. (지우기 약속)
  private String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
