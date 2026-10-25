package com.ithopeschool.projectbe.domain.variant;

import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.ScheduledJob;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 예약 작업 variant-weekly: 매주 월요일에 지난주(월 ~ 일)의 주간 심화 세트를 만듭니다. (docs/VARIANT-SPEC.md 3-4 · 8장)
//
// 월요일(새벽 4시 기준)이 아닌 날에 부르면 아무것도 하지 않습니다. 그래서 cron-job.org 에서 매일 불러도 됩니다.
// variant-daily 처럼 한 번에 job-budget-sec 까지만 새 변형 만들기를 시작하고, 남은 것은 다음 호출이 채웁니다.
@Component
@RequiredArgsConstructor
public class VariantWeeklyJob implements ScheduledJob {

  public static final String NAME = "variant-weekly";

  private static final Logger log = LoggerFactory.getLogger(VariantWeeklyJob.class);

  private final WeeklySetService weeklySetService;
  private final VariantSettings settings;
  private final Clock clock;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void run(JobContext context) {
    if (context.date().getDayOfWeek() != DayOfWeek.MONDAY) {
      log.info("[변형 주간] 월요일이 아니라 건너뜁니다. 기준 날짜={}", context.date());
      return;
    }
    // 지난주 월요일 (오늘이 월요일이므로 7일 전)
    LocalDate weekStart = context.date().minusWeeks(1);
    Instant deadline = context.startedAt().plusSeconds(settings.jobBudgetSec());
    List<Long> users = weeklySetService.candidateUserIds(weekStart);
    int sets = 0;
    for (Long userId : users) {
      if (!clock.instant().isBefore(deadline)) {
        break;
      }
      if (weeklySetService.fill(userId, weekStart, context.date(), deadline).isPresent()) {
        sets++;
      }
    }
    log.info("[변형 주간] {} 주 세트: 대상 학생 {}명, 만들거나 채운 세트 {}개", weekStart, users.size(), sets);
  }
}
