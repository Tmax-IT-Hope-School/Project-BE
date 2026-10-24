package com.ithopeschool.projectbe.domain.summary;

import com.ithopeschool.projectbe.domain.job.JobKeyInterceptor;
import com.ithopeschool.projectbe.domain.summary.dto.DailySummaryRebuildResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 지난 날짜의 하루 요약을 다시 만드는 명령입니다. (이슈 #34 "기록을 고쳤을 때")
// 주소가 /api/v1/jobs/ 아래라서, 예약 작업과 똑같이 문지기(JobKeyInterceptor)가 비밀 키를 먼저 확인합니다.
// 학생이 아니라 관리하는 사람이 부르는 주소입니다. 키가 없거나 틀리면 401 이고 아무것도 만들지 않습니다.
@Tag(name = "예약 작업")
@RestController
@RequestMapping("/api/v1/jobs/" + DailySummaryJob.NAME)
@RequiredArgsConstructor
public class DailySummaryController {

  private final DailySummaryJob dailySummaryJob;

  // 날짜 모양이 틀리거나 빠지면 Spring 이 막고, GlobalExceptionHandler 가 400 VALIDATION_ERROR 로 바꿔 답합니다.
  @Operation(
      summary = "지난 날짜의 하루 요약 다시 만들기 (비밀 키 필요)",
      description = "모든 사용자의 그날 요약을 다시 계산해 덮어씁니다. 여러 번 불러도 결과가 같습니다. 아직 오지 않은 날짜는 400 입니다.")
  @Parameter(
      name = JobKeyInterceptor.KEY_HEADER,
      in = ParameterIn.HEADER,
      required = true,
      description = "비밀 키. 틀리거나 없으면 401")
  @PostMapping("/rebuild")
  public DailySummaryRebuildResponse rebuild(
      @Parameter(description = "다시 만들 날짜. 예: 2026-10-20")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    int userCount = dailySummaryJob.rebuildPastDay(date);
    return new DailySummaryRebuildResponse(
        date.toString(), userCount, date + " 하루 요약을 " + userCount + "명 다시 만들었어요");
  }
}
