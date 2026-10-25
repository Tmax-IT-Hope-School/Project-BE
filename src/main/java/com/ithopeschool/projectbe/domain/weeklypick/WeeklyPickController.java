package com.ithopeschool.projectbe.domain.weeklypick;

import com.ithopeschool.projectbe.domain.weeklypick.dto.WeeklyPickResponse;
import com.ithopeschool.projectbe.domain.weeklypick.dto.WeeklyPickSaveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// "이번 주에 고른 것" API 의 입구입니다. 요청을 받아 WeeklyPickService 를 부르기만 합니다. 로그인이 필요합니다. (이슈 #406)
// 날짜 모양이 틀리면(2026/10/26) Spring 이 막고, GlobalExceptionHandler 가 400 VALIDATION_ERROR 로 바꿔 답합니다.
@Tag(name = "이번 주에 고른 것")
@RestController
@RequestMapping("/api/v1/weekly-picks")
@RequiredArgsConstructor
public class WeeklyPickController {

  private final WeeklyPickService weeklyPickService;

  // 새로 만들 때도, 덮어쓸 때도 200 을 돌려줍니다. (FE 는 어느 쪽인지 신경 쓰지 않아도 됩니다)
  @Operation(summary = "그 주에 해 볼 것 저장 (한 주에 하나 · 다시 보내면 덮어씀 · weekStart 는 월요일 · 토큰 필요)")
  @PutMapping("/{weekStart}")
  public WeeklyPickResponse save(
      @Parameter(description = "그 주의 월요일 날짜. 예: 2026-10-26", required = true)
          @PathVariable
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate weekStart,
      @Valid @RequestBody WeeklyPickSaveRequest request) {
    try {
      return weeklyPickService.save(weekStart, request);
    } catch (DataIntegrityViolationException e) {
      // 같은 주를 같은 순간에 두 번 보내 다른 요청이 먼저 줄을 만들었으면, 다시 불러 그 줄을 덮어씁니다.
      return weeklyPickService.save(weekStart, request);
    }
  }

  @Operation(summary = "그 기간에 시작한 주에 고른 것 목록 (from ~ to 포함 · 오래된 주부터 · 366일까지 · 토큰 필요)")
  @GetMapping
  public List<WeeklyPickResponse> list(
      @Parameter(description = "시작 날짜(포함). 예: 2026-10-01", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "끝 날짜(포함). 예: 2026-10-31", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return weeklyPickService.list(from, to);
  }
}
