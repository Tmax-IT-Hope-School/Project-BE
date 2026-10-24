package com.ithopeschool.projectbe.domain.summary;

import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.JobResultStore;
import com.ithopeschool.projectbe.domain.job.ScheduledJob;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 매일 밤 사용자마다 하루 요약표(daily_summaries)를 만드는 예약 작업입니다. (이슈 #34, docs/JOBS.md "daily-summary")
// cron-job.org 가 POST /api/v1/jobs/daily-summary 를 부릅니다. (예: 매일 새벽 3시 30분 — 하루가 바뀌는 4시 전)
//
// 한 번 돌 때 "오늘" 과 "어제" 를 둘 다 다시 만듭니다. (담당 학생이 고름)
//   - 그날의 미달 원인은 다음 날 체크인에 들어옵니다. 어제 요약을 오늘 다시 만들면 저절로 채워집니다.
//   - 작업이 돈 뒤 새벽 4시 전까지 한 공부도, 다음 날 밤에 어제를 다시 만들 때 들어갑니다.
// 결과는 그날 줄을 덮어쓰기라서, 같은 작업이 두 번 돌아도 결과가 같습니다.
//
// 지난 날짜 하나를 다시 만들 때는 rebuildPastDay 를 씁니다. (DailySummaryController, 기록을 고쳤을 때)
//
// ⚠️ 요약 안의 숫자 · 컨디션 · 기분은 로그에 찍지 않습니다. 몇 명을 만들었는지 숫자만 남깁니다.
@Component
@RequiredArgsConstructor
public class DailySummaryJob implements ScheduledJob {

  // 부르는 주소가 POST /api/v1/jobs/daily-summary 가 됩니다.
  public static final String NAME = "daily-summary";

  private static final Logger log = LoggerFactory.getLogger(DailySummaryJob.class);

  private final DailySummaryService dailySummaryService;
  private final JobResultStore resultStore;
  private final Clock clock;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public void run(JobContext context) {
    LocalDate today = context.date();
    LocalDate yesterday = today.minusDays(1);
    int yesterdayCount = rebuildAll(yesterday);
    int todayCount = rebuildAll(today);

    // 결과는 그날 한 칸에 덮어씁니다. (같은 날 다시 돌아도 같은 글자)
    resultStore.save(
        NAME,
        today,
        "하루 요약 " + yesterday + " " + yesterdayCount + "명, " + today + " " + todayCount + "명");
    log.info("[하루 요약] {} {}명, {} {}명 만들었어요", yesterday, yesterdayCount, today, todayCount);
  }

  // 지난 날짜(또는 오늘) 하나의 요약을 모든 사용자에 대해 다시 만듭니다. 만든 사람 수를 돌려줍니다.
  // 아직 오지 않은 날은 기록이 없어서 "순공 0" 이 저장되므로 막습니다. (400)
  public int rebuildPastDay(LocalDate date) {
    LocalDate today = DayRule.dateOf(clock.instant());
    if (date.isAfter(today)) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "아직 오지 않은 날짜는 만들 수 없어요. 오늘(" + today + ")까지만 돼요");
    }
    int count = rebuildAll(date);
    log.info("[하루 요약] {} 를 다시 만들었어요. {}명", date, count);
    return count;
  }

  // 그날 요약을 사용자마다 만듭니다. 사람마다 따로 저장해서, 중간에 실패해도 앞사람 것은 남습니다.
  // 실패하면 예외가 그대로 나가서 실행기가 "실패" 로 알립니다. 다시 돌리면 덮어쓰기라 안전합니다.
  private int rebuildAll(LocalDate date) {
    int count = 0;
    for (Long userId : dailySummaryService.userIdsOn(date)) {
      dailySummaryService.rebuild(userId, date);
      count++;
    }
    return count;
  }
}
