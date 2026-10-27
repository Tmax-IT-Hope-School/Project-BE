package com.ithopeschool.projectbe.domain.answercheck;

import com.ithopeschool.projectbe.domain.ai.AiClient;
import com.ithopeschool.projectbe.domain.ai.CardPhotoService;
import com.ithopeschool.projectbe.domain.answercheck.dto.AnswerCheckCreateRequest;
import com.ithopeschool.projectbe.domain.answercheck.dto.AnswerCheckItemUpdateRequest;
import com.ithopeschool.projectbe.domain.answercheck.dto.AnswerCheckResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// "사진 채점" 의 로직입니다. 학생이 폰에서 문제집 사진을 올리면 AI 가 채점하고, 결과를 저장하고,
// PC 가 그날 기록을 보고, 학생이 문제별로 판정을 고칩니다. (2026-10-24 사용자 결정)
// ⚠️ 사진은 저장하지 않습니다. 메모리에서 OpenAI 로 보내고 끝이며, DB · 파일 · 로그 어디에도 남기지 않습니다.
//
// 올리기 순서
//   1. 사진 모양을 봅니다. (CardPhotoService.checkImage: jpeg · png · webp 의 data URL, 4MB 까지. 아니면 400)
//   2. 사진과 함께 AI 를 부릅니다. 꺼짐 · 실패 · 시간 초과면 graded:false, reason UNAVAILABLE (기록은 작은 AI 기능 ASSIST)
//   3. AI 가 위기 표현이라고 하거나, 읽은 글에 위기 표현(단어 목록)이 있으면 저장하지 않고 showHelp (#56, 어떤 기능보다 먼저)
//   4. 문제마다 모드에 맞게 다듬습니다. (AnswerGrading.gradedItems) 문제 사진이 아니거나 남은 문제가 없으면 NOT_PROBLEM
//   5. 기록 한 줄과 문제 여러 줄을 한 번에 저장합니다. (중간에 실패하면 하나도 저장하지 않음)
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 기록만 읽고 고칩니다.
@Service
@RequiredArgsConstructor
public class AnswerCheckService {

  private static final Logger log = LoggerFactory.getLogger(AnswerCheckService.class);

  // 기간 조회(getChecksBetween)가 받아 주는 가장 긴 기간(일)입니다. 시작일과 끝일을 모두 센 날 수입니다.
  // 주간 리포트는 7일이면 되지만, 다른 기간 조회(일기 · 미루기 표시)와 같은 31일로 맞췄습니다.
  static final int MAX_RANGE_DAYS = 31;

  private final CurrentUserProvider currentUserProvider;
  private final AnswerCheckRepository answerCheckRepository;
  private final AnswerCheckItemRepository answerCheckItemRepository;
  private final AiClient aiClient;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  // AI 를 기다리는 동안(최대 25초) DB 연결을 붙잡지 않으려고 메서드 전체에 @Transactional 을 붙이지 않았습니다.
  // 저장하는 부분(5번)만 transactionTemplate 으로 묶습니다.
  public AnswerCheckResponse create(AnswerCheckCreateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    AnswerCheckMode mode = request.mode();
    LocalDate date = request.date();

    // 1) 사진 검사
    CardPhotoService.checkImage(request.image());

    // 2) 사진과 함께 AI
    Optional<AnswerGrading.Answer> answer = askAi(userId, mode, request.image());
    // 채점했어요(MARKED)인데 O · X 표시를 하나도 못 읽었으면 한 번만 더 물어봅니다. (2026-10-27 "2번 올려야 인식")
    // 다시 물어도 못 읽으면 처음 답을 그대로 씁니다. AI 를 두 번 기다려서 FE 는 이 요청을 65초까지 기다립니다.
    if (answer.isPresent() && AnswerGrading.readNoMarks(mode, answer.get())) {
      log.info("[사진 채점] O · X 표시를 하나도 못 읽어서 한 번 더 물어봅니다");
      Optional<AnswerGrading.Answer> again = askAi(userId, mode, request.image());
      if (again.isPresent() && !AnswerGrading.readNoMarks(mode, again.get())) {
        answer = again;
      }
    }
    if (answer.isEmpty()) {
      return AnswerCheckResponse.notGraded(AnswerCheckResponse.UNAVAILABLE, date, mode);
    }

    // 3) 위기 표현 (AI 판단 · 읽은 글의 단어 목록)
    List<AnswerGrading.Graded> items = AnswerGrading.gradedItems(mode, answer.get().items());
    if (Boolean.TRUE.equals(answer.get().crisis()) || hasCrisisText(items)) {
      return AnswerCheckResponse.help(date, mode);
    }

    // 4) 문제 사진이 아님 · 읽은 문제가 없음
    if (!Boolean.TRUE.equals(answer.get().problemPage()) || items.isEmpty()) {
      // 왜 NOT_PROBLEM 인지 배포 서버 로그에서 보려고 남깁니다. (2026-10-24 "계속 문제를 못 읽었어요만 나와요")
      // ⚠️ 사진 · 문제 번호 · 답 글자는 찍지 않고 true/false 와 개수 · 크기만 찍습니다.
      int receivedCount = answer.get().items() == null ? 0 : answer.get().items().size();
      log.info(
          "[사진 채점] NOT_PROBLEM: mode={}, problemPage={}, 받은 문제 {}개, 남은 문제 {}개, 사진 {}KB",
          mode,
          answer.get().problemPage(),
          receivedCount,
          items.size(),
          request.image().length() / 1024);
      return AnswerCheckResponse.notGraded(AnswerCheckResponse.NOT_PROBLEM, date, mode);
    }

    // 5) 저장
    String subjectText = AnswerGrading.subjectOf(answer.get().subjectText());
    return transactionTemplate.execute(
        status -> {
          AnswerCheck check =
              answerCheckRepository.save(
                  new AnswerCheck(userId, date, mode, subjectText, clock.instant()));
          List<AnswerCheckItem> rows = new ArrayList<>();
          for (int i = 0; i < items.size(); i++) {
            AnswerGrading.Graded item = items.get(i);
            rows.add(
                new AnswerCheckItem(
                    check.getId(),
                    userId,
                    i,
                    item.number(),
                    item.myAnswer(),
                    item.aiAnswer(),
                    item.markRead(),
                    item.result()));
          }
          return AnswerCheckResponse.of(check, answerCheckItemRepository.saveAll(rows));
        });
  }

  // 사진과 질문을 AI 에게 한 번 보냅니다. 답을 못 받으면(시간 초과 · 키 없음 등) 비어 있습니다.
  private Optional<AnswerGrading.Answer> askAi(Long userId, AnswerCheckMode mode, String image) {
    return aiClient.askWithImage(
        userId,
        GptUsageKind.ASSIST,
        AnswerGrading.question(mode),
        image,
        AnswerGrading.Answer.class);
  }

  // 내 그날의 채점 기록들, 최근에 올린 것부터. PC 가 3초마다 부르므로 DB 는 두 번만 부릅니다. (기록들 · 그 문제들)
  @Transactional(readOnly = true)
  public List<AnswerCheckResponse> getChecks(LocalDate date) {
    Long userId = currentUserProvider.getCurrentUserId();
    List<AnswerCheck> checks =
        answerCheckRepository.findByUserIdAndCheckDateOrderByCreatedAtDescIdDesc(userId, date);
    return withItems(userId, checks);
  }

  // 내 기간(from ~ to, 두 날짜 모두 포함) 채점 기록들. 주간 리포트가 일주일치를 한 번에 읽을 때 씁니다.
  // 최근 날짜부터, 같은 날은 그날 목록(getChecks)과 같은 순서입니다. 기간이 길어도 DB 는 두 번만 부릅니다.
  @Transactional(readOnly = true)
  public List<AnswerCheckResponse> getChecksBetween(LocalDate from, LocalDate to) {
    // 로그인 확인이 먼저입니다. (토큰이 없으면 날짜를 보기 전에 401)
    Long userId = currentUserProvider.getCurrentUserId();
    if (from.isAfter(to)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시작 날짜는 끝 날짜보다 늦을 수 없어요");
    }
    // 시작일과 끝일을 모두 세어야 해서 1 을 더합니다. (같은 날이면 1일)
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "한 번에 " + MAX_RANGE_DAYS + "일까지만 조회할 수 있어요");
    }
    List<AnswerCheck> checks =
        answerCheckRepository
            .findByUserIdAndCheckDateBetweenOrderByCheckDateDescCreatedAtDescIdDesc(
                userId, from, to);
    return withItems(userId, checks);
  }

  // 기록들에 각 기록의 문제를 붙여 응답으로 바꿉니다. 기록 순서는 받은 그대로입니다.
  // 문제는 기록 수와 상관없이 한 번에 꺼냅니다. (기록마다 따로 꺼내면 DB 를 기록 수만큼 부르게 됩니다)
  private List<AnswerCheckResponse> withItems(Long userId, List<AnswerCheck> checks) {
    if (checks.isEmpty()) {
      return List.of();
    }

    // 기록 번호 → 그 기록의 문제들 (사진 속 순서대로)
    Map<Long, List<AnswerCheckItem>> itemsByCheck = new LinkedHashMap<>();
    List<Long> checkIds = checks.stream().map(AnswerCheck::getId).toList();
    for (AnswerCheckItem item :
        answerCheckItemRepository.findByUserIdAndAnswerCheckIdInOrderByAnswerCheckIdAscSortOrderAsc(
            userId, checkIds)) {
      itemsByCheck.computeIfAbsent(item.getAnswerCheckId(), id -> new ArrayList<>()).add(item);
    }
    return checks.stream()
        .map(
            check ->
                AnswerCheckResponse.of(check, itemsByCheck.getOrDefault(check.getId(), List.of())))
        .toList();
  }

  // 문제 하나의 판정을 학생이 고칩니다. 고친 기록 전체를 돌려줍니다.
  // 기록 · 문제 번호가 없거나, 그 기록의 문제가 아니거나, 남의 것이면 404 입니다.
  @Transactional
  public AnswerCheckResponse updateItem(
      Long checkId, Long itemId, AnswerCheckItemUpdateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    AnswerCheck check =
        answerCheckRepository
            .findByIdAndUserId(checkId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "채점 기록을 찾을 수 없어요"));
    AnswerCheckItem item =
        answerCheckItemRepository
            .findByIdAndAnswerCheckIdAndUserId(itemId, checkId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "문제를 찾을 수 없어요"));

    // @Transactional 이라 바뀐 값은 끝날 때 DB 에 저장됩니다.
    item.edit(request.result(), request.correctAnswer(), clock.instant());
    return AnswerCheckResponse.of(
        check,
        answerCheckItemRepository.findByUserIdAndAnswerCheckIdOrderBySortOrderAsc(userId, checkId));
  }

  // AI 가 사진에서 읽은 글(학생 답 · AI 답)에 위기 표현이 있는지 봅니다.
  private static boolean hasCrisisText(List<AnswerGrading.Graded> items) {
    List<String> texts = new ArrayList<>();
    for (AnswerGrading.Graded item : items) {
      texts.add(item.myAnswer());
      texts.add(item.aiAnswer());
    }
    return CrisisText.hasCrisisText(texts);
  }
}
