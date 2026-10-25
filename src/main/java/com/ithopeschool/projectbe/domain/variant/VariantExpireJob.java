package com.ithopeschool.projectbe.domain.variant;

import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.ScheduledJob;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 예약 작업 variant-expire: 마감이 지난 PENDING 과제를 EXPIRED 로 바꿉니다. (docs/VARIANT-SPEC.md 8장)
// 일일 과제는 그날 하루가 마감이라, 날이 바뀐 뒤(새벽 4시 이후) 부르면 전날의 미응답 과제가 모두 바뀝니다.
// 학습 항목은 바꾸지 않아서 그 항목은 오늘 다시 출제 대상이 됩니다. (벌점 없음)
// 몇 번을 불러도 결과가 같습니다. (이미 EXPIRED 인 줄은 다시 바뀌지 않음)
@Component
@RequiredArgsConstructor
public class VariantExpireJob implements ScheduledJob {

  public static final String NAME = "variant-expire";

  private static final Logger log = LoggerFactory.getLogger(VariantExpireJob.class);

  private final DailyAssignmentService dailyAssignmentService;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void run(JobContext context) {
    int expired = dailyAssignmentService.expireOverdue(context.date());
    log.info("[변형 만료] 기준 날짜={}, 마감된 과제 {}개", context.date(), expired);
  }
}
