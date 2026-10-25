package com.ithopeschool.projectbe.domain.variant;

import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.ScheduledJob;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 예약 작업 variant-daily: 학생마다 오늘 낼 변형을 만들고 과제로 배정합니다. (docs/VARIANT-SPEC.md 3-3 · 8장)
//
// 부르는 쪽(cron-job.org)은 30초까지만 기다리므로, 한 번 부를 때 job-budget-sec(20초)까지만 새로 만들기를 시작합니다.
// 남은 학생 · 항목은 다음 호출(10분 뒤)이 이어서 합니다. 이미 배정한 과제는 건너뛰어서 여러 번 불러도 안전합니다.
@Component
@RequiredArgsConstructor
public class VariantDailyJob implements ScheduledJob {

  public static final String NAME = "variant-daily";

  private static final Logger log = LoggerFactory.getLogger(VariantDailyJob.class);

  private final ReviewItemRepository reviewItemRepository;
  private final DailyAssignmentService dailyAssignmentService;
  private final VariantSettings settings;
  private final Clock clock;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void run(JobContext context) {
    Instant deadline = context.startedAt().plusSeconds(settings.jobBudgetSec());
    List<Long> users =
        reviewItemRepository.findUserIdsWithDue(ReviewItemStatus.ACTIVE, context.date());
    int assigned = 0;
    int preparing = 0;
    for (Long userId : users) {
      if (!clock.instant().isBefore(deadline)) {
        break;
      }
      DailyAssignmentService.FillResult result =
          dailyAssignmentService.fill(userId, context.date(), true, deadline);
      assigned += result.assigned();
      preparing += result.preparing();
    }
    // 학생 번호나 문제 내용은 찍지 않고 개수만 남깁니다.
    log.info(
        "[변형 일일] 기준 날짜={}, 학생 {}명, 새 과제 {}개, 아직 준비 중 {}개",
        context.date(),
        users.size(),
        assigned,
        preparing);
  }
}
