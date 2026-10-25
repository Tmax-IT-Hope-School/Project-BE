package com.ithopeschool.projectbe.domain.quizresult;

import com.ithopeschool.projectbe.domain.quizresult.dto.QuizResultRequest;
import com.ithopeschool.projectbe.domain.quizresult.dto.QuizResultResponse;
import com.ithopeschool.projectbe.domain.quizresult.dto.QuizStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 확인 문제(퀴즈) 결과 API 의 입구입니다. QuizResultService 를 부르기만 합니다. (2026-10-24, 통계 탭)
// 두 주소 모두 로그인이 필요합니다.
@Tag(name = "퀴즈 결과 · 통계")
@RestController
@RequestMapping("/api/v1/quiz-results")
@RequiredArgsConstructor
public class QuizResultController {

  private final QuizResultService quizResultService;

  @Operation(summary = "퀴즈 한 번의 결과 저장 (날짜는 서버가 정함 · 토큰 필요)")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public QuizResultResponse save(@Valid @RequestBody QuizResultRequest request) {
    return quizResultService.save(request);
  }

  @Operation(summary = "기간 안의 과목 · 단원별 퀴즈 통계와 과목별 공부 시간 (from~to 포함 · 31일까지 · 토큰 필요)")
  @GetMapping("/stats")
  public QuizStatsResponse getStats(
      @Parameter(description = "시작 날짜(포함). 예: 2026-10-19", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "끝 날짜(포함). 예: 2026-10-25", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return quizResultService.getStats(from, to);
  }
}
