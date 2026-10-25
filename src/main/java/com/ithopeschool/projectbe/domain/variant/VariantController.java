package com.ithopeschool.projectbe.domain.variant;

import com.ithopeschool.projectbe.domain.variant.dto.AttemptRequest;
import com.ithopeschool.projectbe.domain.variant.dto.AttemptResponse;
import com.ithopeschool.projectbe.domain.variant.dto.ClassificationUpdateRequest;
import com.ithopeschool.projectbe.domain.variant.dto.DailyResponse;
import com.ithopeschool.projectbe.domain.variant.dto.ReviewItemResponse;
import com.ithopeschool.projectbe.domain.variant.dto.SourceProblemCreateRequest;
import com.ithopeschool.projectbe.domain.variant.dto.SourceProblemResponse;
import com.ithopeschool.projectbe.domain.variant.dto.VariantQuizSourceRequest;
import com.ithopeschool.projectbe.domain.variant.dto.VariantQuizSourceResponse;
import com.ithopeschool.projectbe.domain.variant.dto.WeeklyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 변형 문제 API 의 입구입니다. 요청을 받아 Service 를 부르기만 합니다. 모두 로그인이 필요합니다.
// (docs/VARIANT-SPEC.md 7장, docs/API.md "변형 문제")
@Tag(name = "변형 문제 (오답 변형 · 주간 심화)")
@RestController
@RequestMapping("/api/v1/variant")
@RequiredArgsConstructor
public class VariantController {

  private final SourceProblemService sourceProblemService;
  private final AttemptService attemptService;
  private final VariantQueryService queryService;
  private final VariantQuizSourceService quizSourceService;

  @Operation(summary = "원본 문항 등록 (맞은 · 틀린 문제 모두 · 틀리고 분류되면 학습 항목 생성 · 토큰 필요)")
  @PostMapping("/source-problems")
  @ResponseStatus(HttpStatus.CREATED)
  public SourceProblemResponse register(@Valid @RequestBody SourceProblemCreateRequest request) {
    return sourceProblemService.register(request);
  }

  @Operation(summary = "확인 문제 결과를 원본으로 (문제마다 정답 확인 · 분류 뒤 저장 · 틀리면 학습 항목 · 찍은 결과는 틀린 문제만 · 토큰 필요)")
  @PostMapping("/quiz-results")
  public VariantQuizSourceResponse quizResults(
      @Valid @RequestBody VariantQuizSourceRequest request) {
    return quizSourceService.submit(request);
  }

  @Operation(summary = "원본의 단원 · 유형을 사람이 정함 (분류 대기 · 확인 필요 원본만 · 토큰 필요)")
  @PatchMapping("/source-problems/{id}/classification")
  public SourceProblemResponse classify(
      @PathVariable Long id, @Valid @RequestBody ClassificationUpdateRequest request) {
    return sourceProblemService.updateClassification(id, request);
  }

  @Operation(summary = "그날의 일일 과제 (정답 없음 · date 없으면 오늘 · 토큰 필요)")
  @GetMapping("/daily")
  public DailyResponse daily(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return queryService.daily(date);
  }

  @Operation(summary = "답 내기 · 채점 (과제당 한 번 · mc5 는 0~4, short 는 정수 · 토큰 필요)")
  @PostMapping("/assignments/{id}/attempts")
  public AttemptResponse submit(@PathVariable Long id, @Valid @RequestBody AttemptRequest request) {
    return attemptService.submit(id, request);
  }

  @Operation(
      summary =
          "주간 심화 세트 (week=돌아보는 주의 월요일 2026-10-19 · 없으면 가장 최근 세트 · includeAnswers 는 답했거나 마감된 문항만 · 토큰 필요)")
  @GetMapping("/weekly")
  public WeeklyResponse weekly(
      @RequestParam(required = false) String week,
      @RequestParam(defaultValue = "false") boolean includeAnswers) {
    return queryService.weekly(week, includeAnswers);
  }

  @Operation(summary = "오답노트 현황 (status=active · graduated · mastered, 없으면 전부 · 토큰 필요)")
  @GetMapping("/review-items")
  public List<ReviewItemResponse> reviewItems(@RequestParam(required = false) String status) {
    return queryService.reviewItems(status);
  }
}
